# Architecture: Cache Library + Live Metrics Panel

**Companion to:** `PRD.md`, `TECH_STACK.md` | **Style:** modular monolith, single JVM, one browser client
**Diagrams:** Mermaid (render in GitHub, VS Code, or any Markdown viewer with Mermaid support)

---

## 1. Architectural stance

**System promise:** a cache whose behavior is *provable* (correct under concurrency) and *visible* (live hit/miss/eviction/expiry metrics for any policy and traffic pattern).

| Decision | Choice | Trade-off accepted | Reversibility |
|---|---|---|---|
| Deployment shape | Modular monolith, 1 JVM | No independent scaling; no network failure modes to debug | Easy: modules already separated |
| Cache core isolation | `cache-core` is pure JDK, no framework | Slightly more glue in the server | **Hard to reverse, so decided first** |
| Policy abstraction | `EvictionPolicy<K>` strategy interface | One extra indirection | **Hard to reverse; frozen at minute 0** |
| Concurrency | One global `ReentrantLock` | Throughput ceiling; correct by construction | Medium: lock is encapsulated in `Cache`, so striping later changes one class |
| TTL ownership | `Cache` owns expiry, policies never see time | Expiry checks add a branch to every read | Medium |
| Live transport | SSE | One-way only (fine: control uses REST) | Easy |
| State | In-memory only | Runs are lost on restart | Easy |

**Boundary owners** (assign names in the README): `cache-core` (owner A), `cache-server` (owner B), `cache-ui` (owner C). Cross-boundary contracts change only by agreement of both owners.

## 2. System context

```mermaid
flowchart LR
    Op(["Operator (browser)"])
    subgraph JVM["Single JVM: cache-server (Spring Boot)"]
        API["REST + SSE Controller"]
        RM["RunManager"]
        SIM["Simulator: TraceGenerator + Workers"]
        SMP["Sampler (200 ms)"]
        CORE["cache-core: Cache + Policies"]
    end
    UI["cache-ui (React, Vite dev server)"]
    Op --> UI
    UI -- "REST: start/stop/get" --> API
    API -- "SSE: sample events" --> UI
    API --> RM
    RM --> SIM
    RM --> SMP
    SIM -- "get / put" --> CORE
    SMP -- "read stats" --> CORE
```

Dev topology: browser → Vite `:5173` → proxy `/api` → Spring `:8080`.

## 3. Module and component view

```mermaid
flowchart TB
    subgraph UI["cache-ui (owner C)"]
        CP["ConfigPanel"]
        HK["useRunStream hook"]
        CH["HitRateChart"]
        KP["KpiCard / CounterGrid"]
        SB["StatusBar"]
        CP --> HK
        HK --> CH
        HK --> KP
        HK --> SB
    end
    subgraph SRV["cache-server (owner B)"]
        RC["RunController"]
        RMG["RunManager"]
        RUN["Run (state, cache, workers, sampler, emitters)"]
        TG["TraceGenerator"]
        WK["Worker x N"]
        SP["Sampler"]
        RC --> RMG --> RUN
        RUN --> TG
        RUN --> WK
        RUN --> SP
    end
    subgraph CORE["cache-core (owner A, pure JDK)"]
        CA["Cache"]
        EP["EvictionPolicy"]
        LRU["LruPolicy"]
        LFU["LfuPolicy"]
        ST["CacheStats (LongAdders)"]
        CA --> EP
        LRU -. implements .-> EP
        LFU -. implements .-> EP
        CA --> ST
    end
    HK -- "REST + SSE" --> RC
    WK --> CA
    SP --> ST
    SP --> RUN
```

**Dependency rule:** `cache-ui → (HTTP) → cache-server → cache-core`. `cache-core` depends on nothing. Never the reverse.

## 4. End-to-end run flow (the main working flow)

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operator
    participant UI as cache-ui
    participant API as RunController
    participant RM as RunManager
    participant R as Run
    participant W as Workers (N threads)
    participant C as Cache
    participant S as Sampler

    Op->>UI: pick config, click Run
    UI->>API: POST /api/runs (config)
    API->>API: validate config (400 on failure)
    API->>RM: start(config)
    RM->>RM: stop previous run if active
    RM->>R: create Run (builds Cache, generates trace)
    RM-->>API: runId
    API-->>UI: 201 {runId}
    UI->>API: GET /api/runs/{id}/stream (SSE)
    API->>R: register SseEmitter, replay latest sample
    RM->>R: launch
    par workers
        R->>W: start N workers on trace slices
        loop each op in slice
            W->>C: get(key) or put(key, value)
            alt get missed
                W->>W: simulate loader (sleep loaderLatencyMs)
                W->>C: put(key, value, ttl)
            end
        end
    and sampler
        loop every 200 ms
            S->>C: read counters
            S->>S: compute rates, windowed hit rate, ops/sec
            S-->>UI: SSE event "sample"
        end
    end
    W-->>R: all workers finished
    R->>S: final sample, status DONE
    S-->>UI: SSE "sample" (status DONE)
    R->>API: complete emitters
    UI->>Op: chart frozen, final numbers, Run again enabled
```

### Run lifecycle

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> RUNNING: POST /runs
    RUNNING --> DONE: all ops completed
    RUNNING --> STOPPED: POST /runs/id/stop or new run started
    RUNNING --> FAILED: uncaught worker error
    DONE --> [*]
    STOPPED --> [*]
    FAILED --> [*]
```

## 5. Cache internals (the core of the project)

### 5.1 Class model

```mermaid
classDiagram
    class Cache~K,V~ {
        -int capacity
        -Map~K,Entry~ map
        -EvictionPolicy~K~ policy
        -ReentrantLock lock
        -LongSupplier ticker
        -CacheStats stats
        +get(K) V
        +put(K,V) void
        +put(K,V,long ttlMs) void
        +remove(K) V
        +size() int
        +clear() void
        +stats() CacheStats
        ~checkInvariants() void
    }
    class Entry~K,V~ {
        +K key
        +V value
        +long expiresAtNanos
    }
    class EvictionPolicy~K~ {
        <<interface>>
        +onInsert(K)
        +onAccess(K)
        +onRemove(K)
        +selectVictim() Optional~K~
        +clear()
    }
    class LruPolicy~K~ {
        -Map~K,Node~ index
        -Node head
        -Node tail
    }
    class LfuPolicy~K~ {
        -Map~K,Integer~ freqOf
        -Map~Integer,LinkedHashSet~K~~ buckets
        -int minFreq
    }
    class CacheStats {
        +LongAdder hits
        +LongAdder misses
        +LongAdder puts
        +LongAdder evictions
        +LongAdder expirations
        +hitRate() double
    }
    Cache --> Entry
    Cache --> EvictionPolicy
    Cache --> CacheStats
    EvictionPolicy <|.. LruPolicy
    EvictionPolicy <|.. LfuPolicy
```

### 5.2 GET flow (`get` is a write because it mutates policy state)

```mermaid
flowchart TD
    A["get(key)"] --> B["lock"]
    B --> C{"entry in map?"}
    C -- no --> M["stats.misses++"]
    C -- yes --> D{"now >= expiresAt and expiresAt != 0?"}
    D -- yes --> E["map.remove(key); policy.onRemove(key); stats.expirations++; stats.misses++"]
    D -- no --> F["policy.onAccess(key); stats.hits++"]
    F --> G["return value"]
    E --> N["return null"]
    M --> N
    G --> U["unlock"]
    N --> U
```

### 5.3 PUT flow

```mermaid
flowchart TD
    A["put(key, value, ttl)"] --> B["lock"]
    B --> C{"key already in map?"}
    C -- yes --> D["replace value; refresh expiresAt; policy.onAccess(key)"]
    C -- no --> E{"size >= capacity?"}
    E -- yes --> F["victim = policy.selectVictim()"]
    F --> G{"victim expired?"}
    G -- yes --> H["remove victim; stats.expirations++"]
    G -- no --> I["remove victim; stats.evictions++"]
    H --> J["insert new entry; policy.onInsert(key)"]
    I --> J
    E -- no --> J
    D --> K["stats.puts++"]
    J --> K
    K --> L["unlock"]
```

**TTL independence (FR-6):** policies only see `onInsert/onAccess/onRemove` and never a clock. Expiry decisions are made by `Cache`, and removal always goes through one private method `removeInternal(key)` that updates map + policy together, so no ghost entries can remain in either structure.

### 5.4 Policy data structures

**LRU** (all ops O(1)):
```
head <-> [MRU] <-> ... <-> [LRU] <-> tail        index: HashMap<K, Node>
onAccess/onInsert: move/add node right after head   selectVictim: node before tail
```

**LFU** (all ops O(1)):
```
freqOf:   HashMap<K, Integer>
buckets:  HashMap<Integer, LinkedHashSet<K>>     (insertion order = recency within a frequency)
minFreq:  int

onInsert(k):  freqOf[k]=1; buckets[1].add(k); minFreq=1
onAccess(k):  f=freqOf[k]; buckets[f].remove(k); if buckets[f] empty and f==minFreq: minFreq++
              freqOf[k]=f+1; buckets[f+1].add(k)
selectVictim: first element of buckets[minFreq]      (LRU tie-break within a frequency)
onRemove(k):  remove from buckets[freqOf[k]]; if that bucket is empty and it was minFreq, recompute lazily on next insert (minFreq resets to 1)
```

### 5.5 Concurrency model

| Aspect | Design |
|---|---|
| Mutual exclusion | Single `ReentrantLock` per `Cache`; every public operation takes it (including `get`) |
| Lock scope | Map + policy + TTL check only. No sleeps, loaders, JSON, or logging inside |
| Counters | `LongAdder`, read without the lock by the sampler (slightly stale is acceptable; note in README) |
| Deadlock | Impossible by design: one lock, no nested acquisition, no callbacks under the lock |
| Visibility | Fields guarded by the lock; `stats` counters are thread-safe on their own |
| Known limit | Global lock serializes ops; striped segments are the documented next step |

## 6. Server components

| Component | Responsibility | Notes |
|---|---|---|
| `RunController` | REST + SSE endpoints, DTO validation, error mapping to `400` | Thin; no logic |
| `RunManager` | Owns the active run (group, in compare mode); stops the previous one; `runId → Run` map | `AtomicReference`/`ConcurrentHashMap`; bounded history (last 5 runs) |
| `Run` | Holds `RunConfig`, `Cache`, trace, `Status`, `AtomicLong opsCompleted`, worker pool, sampler, emitters, latest sample | One per policy; compare mode creates two `Run`s that share a `compareId` |
| `TraceGenerator` | Builds `int[] trace` up front from `(pattern, keySpace, totalOps, zipfSkew, seed)` | Deterministic; same seed gives the identical trace |
| `Worker` | Processes trace slice `[from, to)`; per-op decision with `SplittableRandom(seed+idx)`: `get` with prob `readRatio`, else `put`. On `get` miss: sleep `loaderLatencyMs`, then `put` | Checks `cancelled` flag each op |
| `Sampler` | Every 200 ms builds a `Sample` from counter deltas; pushes to emitters; sends the final sample on completion | Daemon `ScheduledExecutorService` |

### Trace patterns

| Pattern | Generation |
|---|---|
| `ZIPFIAN` | Precompute CDF over `keySpace` with weight `1/rank^s`; draw uniform, binary search |
| `SEQUENTIAL_SCAN` | `key = i % keySpace` |
| `UNIFORM` | `rng.nextInt(keySpace)` |
| `HOT_SET_SHIFT` | Zipfian, but keys are remapped by an offset of `keySpace/2` after 50% of the ops |

### Sample computation

```
hitRate        = hits / max(1, hits + misses)                 (cumulative)
missRate       = 1 - hitRate
windowHitRate  = dHits / max(1, dHits + dMisses)              (since previous sample)
opsPerSec      = dOps / dSeconds
tMs            = now - runStart
```

## 7. Live delivery and resilience

```mermaid
flowchart LR
    S["Sampler"] --> L["Run.latestSample (volatile)"]
    S --> E1["SseEmitter 1"]
    S --> E2["SseEmitter n"]
    E1 --> UI["useRunStream"]
    L -. "GET /runs/id (fallback poll)" .-> UI
    UI --> CHART["Chart buffer, last 300 points"]
```

| Failure | Behavior |
|---|---|
| SSE connection drops | `EventSource` auto-reconnects; the hook falls back to polling `GET /runs/{id}` every 500 ms; new subscribers get the latest sample replayed immediately |
| Emitter send fails | Remove that emitter; run continues |
| Invalid config | `400 {"error": "..."}`; UI shows the message inline; no run created |
| Worker throws | Run status becomes `FAILED`; sampler sends a final sample with the status; workers stopped |
| New Run while running | Previous run is cancelled (`STOPPED`); its stream ends with a final sample |
| Stop clicked | `cancelled=true`; `shutdownNow()` on workers; status `STOPPED` within 1 s |
| Server restart | State lost by design (in-memory); UI shows an idle state |

## 8. Compare mode (Tier 1)

`POST /api/compare` creates two `Run`s (LRU and LFU) that use **identical config and trace** (same seed) but **separate `Cache` instances**. Workers for both start together; each run has its own sampler stream. The UI subscribes to both streams, overlays the two hit-rate lines, and computes the verdict from the two final samples:

```
delta = hitRate(LFU) - hitRate(LRU)   →   "LFU +12.4 pts hit rate on ZIPFIAN"
```

Compare mode is the one exception to "single active run": it is a run *group*; a new start or compare stops the whole group.

## 9. Threading model

| Thread pool | Size | Lifecycle |
|---|---|---|
| Tomcat request threads | Default | Spring managed |
| Worker pool (per run) | `config.threads` (1..64) | Created at run start, shut down at finish/stop |
| Sampler (per run) | 1, daemon | Cancelled at finish/stop |
| TTL sweeper (Tier 2, per cache) | 1, daemon, 100 ms | Removes expired entries under the same lock via `removeInternal` |

Total threads at peak: `threads + samplers + sweepers + Tomcat`, well within a laptop's limits at the validated cap.

## 10. Quality attributes and how the architecture delivers them

| Attribute | Mechanism |
|---|---|
| Correctness | Single lock; single removal path keeps map and policy identical; `checkInvariants()` used in stress tests |
| Determinism (demo) | Seeded trace generated before the run; per-thread seeded RNG. Exact counts vary under multithreading; rates are stable |
| Observability | Every event that changes state is counted (`hits, misses, puts, evictions, expirations`) |
| Testability | Injectable ticker (no sleeps in TTL tests); core has no framework dependencies |
| Evolvability | Add policies by implementing one interface; add striping by changing only `Cache` internals |
| Safety | Hard caps on `threads`, `totalOps`, `capacity`, `keySpace` in validation |

## 11. Deliberately not built (state as roadmap in the pitch)

Striped/segmented locking, Caffeine-style buffered reads, W-TinyLFU admission, read-through loader with single-flight, distributed cache, persistence, metrics export (Prometheus), authentication.

## 12. Architecture review checklist (for the demo Q&A)

| Question | Answer |
|---|---|
| Why is `get` under the lock? | It mutates policy state (recency/frequency), so it's a write |
| How do you avoid deadlock? | One lock, no nesting, no callbacks under it |
| How does TTL interact with eviction? | Independent: `Cache` checks time; policies never see it; both structures are updated by one removal path |
| What happens to LFU counts on re-put? | Frequency is kept, TTL is refreshed |
| What is approximate? | Sampler reads counters without the lock, so a sample can be off by a few operations |
| What would you change to scale? | Stripe into N segments with their own lock and policy; eviction becomes per-segment approximate |
| What was hard to reverse? | The `EvictionPolicy` interface and the framework-free core, so they were frozen first |
