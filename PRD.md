# PRD: Custom Cache Library with Live Metrics Panel

**Build window:** 3 hours | **Type:** Working prototype | **Language:** Java 17 + React
**Audience for this doc:** AI coding agent (implement exactly; ask nothing; use the defaults stated here)

---

## 1. Product summary

A framework-free Java cache library (LRU + LFU, per-entry TTL, thread-safe) plus a web dashboard. The operator picks a config, clicks **Run**, a simulator hammers the cache with multithreaded traffic, and the UI plots hit/miss rate live with eviction and TTL-expiration counters.

**Core value:** make eviction-policy behavior visible and provable (correctness under concurrency, measurable hit rate).

## 2. Goals / Non-goals

**Goals**
1. Satisfy every minimum requirement in the problem statement (Section 3).
2. Live, smooth metrics visualization driven by a real multithreaded run.
3. Correctness that can be demonstrated (unit + concurrency stress tests).

**Non-goals (do NOT build)**
Auth, persistence, Docker/cloud deploy, distributed cache, striped locking, JMH benchmarks, W-TinyLFU, multi-user sessions, i18n.

## 3. Requirement traceability

| Problem-statement requirement | Covered by |
|---|---|
| In-memory cache | FR-1 |
| LRU eviction | FR-2 |
| LFU eviction | FR-3 |
| Selectable eviction policy | FR-4 |
| Per-entry TTL | FR-5 |
| TTL independent of eviction | FR-6 |
| Thread-safe concurrent GET/PUT | FR-7 |
| Frontend metrics panel | FR-12..FR-15 |
| Hit rate / miss rate display | FR-13 |
| Metrics against a sample access pattern | FR-9, FR-10 |

## 4. Architecture decisions

**Modular monolith, three boundaries in one repo:**

| Module | Responsibility | Constraint |
|---|---|---|
| `cache-core` | Cache, policies, TTL, locking, counters | **Pure Java. No Spring or web imports.** This is the hard-to-reverse decision |
| `cache-server` | Simulator, run manager, REST + SSE | Thin adapter over `cache-core` |
| `cache-ui` | React dashboard | Talks only to `cache-server` REST/SSE |

**Trade-offs (state in README and pitch):**
- Single global lock: correct and simple; throughput ceiling is low. Striped locking is the documented next step.
- Single JVM: no independent scaling, and no network failure modes to debug in 3 hours.

## 5. Functional requirements

### Cache core (`cache-core`)

| ID | Requirement |
|---|---|
| FR-1 | `Cache<K,V>` with fixed `capacity` (entry count), backed by `HashMap`. API: `V get(K)`, `void put(K, V)`, `void put(K, V, long ttlMs)`, `V remove(K)`, `int size()`, `void clear()`, `CacheStats stats()` |
| FR-2 | **LRU**: `HashMap` + doubly linked list with sentinel head/tail. `get` and `put` move the node to MRU. Evict LRU tail. O(1) |
| FR-3 | **LFU** O(1): `Map<K,Node>`, `Map<Integer, LinkedHashSet<K>>` freq buckets, `minFreq`. Evict the first key of bucket `minFreq` (**LRU tie-break inside a frequency**). New insert sets `minFreq = 1` |
| FR-4 | `EvictionPolicy<K>` interface: `onInsert(K)`, `onAccess(K)`, `onRemove(K)`, `Optional<K> selectVictim()`, `clear()`. Selected at construction via builder: `Cache.builder().capacity(n).policy(Policy.LRU\|LFU).defaultTtlMs(ms).ticker(t).build()` |
| FR-5 | Per-entry TTL: entry stores `expiresAtNanos` (0 = never). `put` without TTL uses `defaultTtlMs` (0 = never expires). Time source is an injectable `LongSupplier ticker` (default `System.nanoTime`) |
| FR-6 | **TTL is independent of the policy.** Expiry logic lives in the cache, not in policies. An expired entry is removed from **both** the map and the policy structure. A high-frequency LFU entry still expires |
| FR-7 | Thread safety: one `ReentrantLock` guards all map + policy + TTL mutation, **including `get`** (it mutates policy state). No user code runs under the lock |
| FR-8 | Metrics via `LongAdder`: `hits, misses, puts, evictions, expirations`. Expired-on-read counts as a **miss** and an **expiration**. `hitRate = hits / max(1, hits+misses)` |

**Semantics (fixed, do not deviate):**
- Re-`put` of an existing key: replace value, refresh TTL, keep LFU frequency, count as access for LRU, never evict.
- Eviction on insert when `size == capacity`: first drop any expired entry found by lazy check if cheap; otherwise `policy.selectVictim()`.
- Null keys and values are rejected (`NullPointerException`).
- `capacity >= 1`, validated in the builder.

### Simulator and runs (`cache-server`)

| ID | Requirement |
|---|---|
| FR-9 | **Trace generator**, seeded RNG, produces `totalOps` keys up front for these patterns: `ZIPFIAN` (param `zipfSkew`, default 1.0), `SEQUENTIAL_SCAN` (cycles keys 0..keySpace-1), `UNIFORM`, `HOT_SET_SHIFT` (Zipfian whose hot set changes at 50% of the trace) |
| FR-10 | **Run execution**: trace is split across `threads` worker threads. Each op: with probability `readRatio` do `get(key)`; on **miss**, simulate a loader (sleep `loaderLatencyMs`, then `put(key, value)` using TTL from config). Otherwise do a direct `put(key, value)` (a write). Runs are async; only one active run at a time (a new Run stops the previous) |
| FR-11 | **Sampler**: every 200 ms during a run, snapshot the cache stats and push to subscribers. Final snapshot is sent on completion with `status=DONE` |

### Dashboard (`cache-ui`)

| ID | Requirement |
|---|---|
| FR-12 | **Config panel**: policy (LRU/LFU), capacity, keySpace, pattern, zipfSkew (only for Zipfian patterns), totalOps, threads, readRatio, ttlMs (0 = none), loaderLatencyMs, seed. Sensible defaults prefilled. **Run** and **Stop** buttons |
| FR-13 | **Hit rate and miss rate** shown as KPI cards (large numbers, current value) **and** a live line chart with two series over time |
| FR-14 | **Counter cards**: evictions, TTL expirations, hits, misses, current size / capacity, ops/sec |
| FR-15 | Run status indicator (IDLE / RUNNING / DONE / STOPPED) and progress bar (opsCompleted / totalOps) |
| FR-16 *(Tier 1)* | **Compare mode**: run the identical seeded trace through LRU and LFU concurrently (two cache instances), show two hit-rate lines on one chart plus a one-line verdict when both finish (e.g. "LFU +12.4 pts hit rate on ZIPFIAN") |
| FR-17 *(Tier 2)* | Active TTL sweeper (daemon `ScheduledExecutorService`, 100 ms) |
| FR-18 *(Tier 2)* | Live entries table (key, freq, TTL remaining) |

## 6. API contract

Base path `/api`. JSON. CORS enabled for the UI dev origin.

**`POST /api/runs`** → `201 {"runId":"..."}`

```json
{
  "policy": "LRU",
  "capacity": 100,
  "keySpace": 1000,
  "pattern": "ZIPFIAN",
  "zipfSkew": 1.0,
  "totalOps": 200000,
  "threads": 8,
  "readRatio": 0.9,
  "ttlMs": 0,
  "loaderLatencyMs": 1,
  "seed": 42
}
```

**`GET /api/runs/{runId}/stream`** → SSE, event name `sample`, one per ~200 ms, final one has `status: "DONE"`:

```json
{
  "runId": "...", "status": "RUNNING", "tMs": 1200,
  "opsCompleted": 48000, "totalOps": 200000,
  "hits": 31000, "misses": 4200, "hitRate": 0.88, "missRate": 0.12,
  "windowHitRate": 0.90,
  "evictions": 3100, "expirations": 0,
  "size": 100, "capacity": 100, "opsPerSec": 41000
}
```

`windowHitRate` = hit rate over the last sampling interval (delta counters); the chart plots `windowHitRate` and `missRate` complement by default, KPI cards show cumulative `hitRate`.

**`POST /api/runs/{runId}/stop`** → `200`, status becomes `STOPPED`.
**`GET /api/runs/{runId}`** → latest snapshot (fallback if SSE drops).
**`POST /api/compare`** *(Tier 1)*: same body as `/runs` minus `policy`; returns `{"runIds":{"LRU":"...","LFU":"..."}}`. UI subscribes to both streams.

Errors: `400` with `{"error":"..."}` for invalid config (capacity < 1, threads < 1 or > 64, totalOps > 5,000,000, readRatio outside 0..1, unknown enum).

## 7. Non-functional requirements

| ID | Requirement |
|---|---|
| NFR-1 | Stress test with 16 threads and at least 1M mixed ops finishes with no exception and no deadlock |
| NFR-2 | Invariants always hold: `size <= capacity`; `hits + misses == total gets`; map key set == policy key set (expose package-private `checkInvariants()`) |
| NFR-3 | `get`/`put` are O(1) amortized in the policy structures (no scans) |
| NFR-4 | UI updates at ~5 Hz without jank; chart keeps at most 300 points (drop oldest) |
| NFR-5 | Sweeper and worker threads are daemon or shut down cleanly; JVM exits on Ctrl+C |
| NFR-6 | TTL tests use an injected fake ticker, never `Thread.sleep` |
| NFR-7 | Whole system starts with two commands: server (`mvn spring-boot:run`) and UI (`npm run dev`) |

## 8. UX flow (summary; visual spec in the separate design doc)

1. Land on a single-page dashboard: config panel (left/top), results area (right/below), empty-state chart with a hint.
2. Operator adjusts config (or uses defaults) and clicks **Run**.
3. Status flips to RUNNING, progress bar advances, KPI cards and chart update live ~5 Hz.
4. Run completes: status DONE, final numbers frozen, **Run again** is available.
5. *(Tier 1)* Toggle **Compare** to run both policies on the same trace and see the verdict line.

Style direction: modern minimal, clean, generous whitespace, light theme with dark-mode toggle optional.

## 9. Data model

```java
record RunConfig(Policy policy, int capacity, int keySpace, Pattern pattern,
                 double zipfSkew, int totalOps, int threads, double readRatio,
                 long ttlMs, long loaderLatencyMs, long seed) {}

record Sample(String runId, Status status, long tMs, long opsCompleted, long totalOps,
              long hits, long misses, double hitRate, double missRate, double windowHitRate,
              long evictions, long expirations, int size, int capacity, double opsPerSec) {}

enum Policy { LRU, LFU }
enum Pattern { ZIPFIAN, SEQUENTIAL_SCAN, UNIFORM, HOT_SET_SHIFT }
enum Status { IDLE, RUNNING, DONE, STOPPED, FAILED }
```

## 10. Repo layout

```
cache-hackathon/
  pom.xml                       (parent, modules: cache-core, cache-server)
  cache-core/  src/main/java/.../{Cache,CacheBuilder,CacheStats,Entry}
               .../policy/{EvictionPolicy,LruPolicy,LfuPolicy}
               src/test/java/...  (unit + stress)
  cache-server/ src/main/java/.../{RunController,RunManager,TraceGenerator,Sampler,dto/*}
  cache-ui/    (Vite + React + TS)  src/{components,hooks,api,types}
  README.md    (architecture diagram, trade-offs, how to run)
```

## 11. Acceptance criteria

| # | Criterion |
|---|---|
| AC-1 | LRU test: capacity 3, put A,B,C, get A, put D → B evicted |
| AC-2 | LFU test: A accessed 3x, B 1x, C 1x (B older) → inserting D evicts B (tie-break by recency) |
| AC-3 | TTL test with fake ticker: entry expires after ttl regardless of policy, including a high-frequency LFU entry; read after expiry is a miss and increments `expirations` |
| AC-4 | Re-put refreshes TTL and does not increase `size` |
| AC-5 | 16-thread stress: NFR-2 invariants hold |
| AC-6 | On `SEQUENTIAL_SCAN` with keySpace > capacity, LRU hit rate is near 0; on `ZIPFIAN` LFU hit rate >= LRU hit rate (same seed) |
| AC-7 | UI shows live-updating hit-rate and miss-rate values and chart during a run, plus eviction and expiration counters |
| AC-8 | Switching policy in the config and re-running uses the newly selected policy |
| AC-9 | Stop button halts a run within 1 s |
| AC-10 | Invalid config returns `400` and the UI shows the error inline |

## 12. Defaults

`policy=LRU, capacity=100, keySpace=1000, pattern=ZIPFIAN, zipfSkew=1.0, totalOps=200000, threads=8, readRatio=0.9, ttlMs=0, loaderLatencyMs=1, seed=42`

## 13. Build order (3 hours)

| Time | Deliverable |
|---|---|
| 0:00-0:15 | Freeze contracts (Sections 6 and 9), scaffold repo |
| 0:15-1:15 | Parallel: core + tests / server + trace generator + sampler / UI scaffold with mock SSE |
| 1:15-2:00 | Integration, real SSE end to end |
| 2:00-2:30 | Compare mode (FR-16), stress test, TTL scenario |
| 2:30-2:45 | Feature freeze, README, diagram |
| 2:45-3:00 | Two demo dry runs |

**Cut order if behind:** FR-18, FR-17, FR-16, `HOT_SET_SHIFT`. Never cut tests AC-1..AC-5 or the dry runs.

## 14. Demo script (~2 min)

1. Zipfian on LFU: high hit rate, counters climb.
2. Sequential scan on LRU: hit rate collapses toward zero.
3. Set `ttlMs` (e.g. 500): expirations counter rises and hit rate drops regardless of policy.
4. *(Tier 1)* Compare on Zipfian: verdict line appears.
5. Close: the trade-off slide (global lock now, striped next).

## 15. Risks

| Risk | Mitigation |
|---|---|
| Integration slips | Frozen contract at 0:15; UI built against mock SSE |
| Multithreaded counts are non-deterministic | Trace is seeded and generated up front; cite rates, not exact counts |
| SSE drop mid-demo | `GET /api/runs/{id}` polling fallback in the UI hook |
| Cold JIT makes the first run slow | Run one warm-up run before presenting |
| Scope creep | Tier gating in Section 5; cut order in Section 13 |
