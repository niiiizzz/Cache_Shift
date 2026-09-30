# CacheLab: High-Performance In-Memory Cache & Simulation Dashboard

A framework-free, thread-safe Java in-memory cache library supporting **LRU** and **LFU** eviction policies with **per-entry TTL**, accompanied by a multi-threaded workload simulation server and a real-time reactive React dashboard with live metrics and head-to-head comparison mode.

---

## 🚀 Quick Start

### Prerequisites
- **Java 17+** (JDK 17 or higher)
- **Maven 3.8+**
- **Node.js 18+** / npm

### 1. Build & Run the Backend Server
```bash
# From root directory
mvn clean install -DskipTests
cd cache-server
mvn spring-boot:run
```
The server will start on `http://localhost:8080`.

### 2. Start the React UI Dashboard
```bash
cd cache-ui
npm install
npm run dev
```
Open `http://localhost:5173` in your browser.

---

## 🏛 Architecture Overview

```mermaid
graph TD
    UI["React Dashboard (Vite + Tailwind + Recharts)"] -->|REST API: /api/runs| SVR["Spring Boot Simulation Server"]
    SVR -->|SSE Stream: /api/runs/{id}/stream| UI
    
    subgraph cacheServer ["cache-server"]
        SVR --> RM["RunManager"]
        RM --> R["Simulation Run"]
        R --> TG["TraceGenerator"]
        R --> WP["Worker Thread Pool (1..64)"]
        R --> SMP["Metrics Sampler (200ms daemon)"]
    end

    subgraph cacheCore ["cache-core (Pure JDK 17)"]
        WP --> C["Cache"]
        C --> L["ReentrantLock"]
        C --> M["ConcurrentHashMap Index"]
        C --> P{"EvictionPolicy"}
        P --> LRU["LruPolicy (Doubly-Linked List)"]
        P --> LFU["LfuPolicy (Freq-Bucketed Sets)"]
        C --> ST["CacheStats (LongAdders)"]
        SMP -.->|Read Lock-Free| ST
    end
```

### Module Boundaries

1. **`cache-core` (Zero external runtime dependencies)**:
   - **`Cache<K,V>`**: Core cache container. Single `ReentrantLock` guarantees linearizable state updates.
   - **`EvictionPolicy<K>`**: Pluggable eviction strategy (`LruPolicy`, `LfuPolicy`).
   - **`LruPolicy`**: Custom sentinel-based doubly-linked list providing $O(1)$ touch, insert, and evict.
   - **`LfuPolicy`**: Frequency map + linked-hash-set buckets with $O(1)$ access and LRU tie-breaking for equal frequencies.
   - **Per-entry TTL**: Evaluated on read (`get`) and insert (`put`) using an injectable `LongSupplier` ticker without blocking on external timers.
   - **`CacheStats`**: Thread-safe, non-blocking metrics via JDK `LongAdder`.

2. **`cache-server`**:
   - Multi-threaded synthetic workload driver generating Zipfian, Uniform, Sequential Scan, and Hot-Set Shift key traces.
   - Background sampler streaming real-time performance snapshots (`Sample`) via Server-Sent Events (`SSE`) every 200ms.
   - REST endpoints for single-run lifecycle and dual-run head-to-head comparison (`POST /api/runs/compare`).

3. **`cache-ui`**:
   - Modern React 18 + TypeScript + Tailwind CSS dashboard with dark/light themes.
   - Real-time hit/miss rate charts powered by Recharts with no animation lag.
   - 1-click workload presets, live KPI cards, occupancy meters, and automatic winner verdicts.

---

## 📊 Performance & Stress Verification

### Unit & Concurrency Test Results
- **`CacheUnitTest` (10/10 PASS)**:
  - `AC-1`: LRU eviction order strictly verified.
  - `AC-2`: LFU frequency counting with deterministic LRU tie-break.
  - `AC-3`: Per-entry TTL independent of policy, verified via `FakeTicker`.
  - `AC-4`: Re-put refreshes TTL while keeping size invariant.
  - `AC-5`: **16 concurrent worker threads × 1,040,000 mixed operations** executed in < 900ms per run with zero invariant violations.
- **`ServerTestRunner` (6/6 PASS)**:
  - **Zipfian Distribution**: LFU outperforms LRU (**66.3% hit rate** vs **57.8% hit rate** on skew 1.2).
  - **Sequential Scan**: LRU collapses to ~0% hit rate as full scans displace cached items.
  - **Graceful Stop**: Active runs halt in < 350ms upon cancellation.
  - **Trace Sharing**: Dual-run compare mode executes identical key sequences in parallel.

---

## ⚖️ Trade-offs & Design Decisions

| Component | Design Choice | Rationale & Trade-offs |
|---|---|---|
| **Locking Strategy** | Single `ReentrantLock` | Prioritizes correctness, linearizability, and simplicity under hackathon deadline. Read path takes exclusive lock because reads update LRU/LFU policy ordering. Trade-off: Maximum throughput is bounded on 32+ cores. Next iteration would use striped locks or lock-free read rings (e.g., Caffeine-style ring buffers). |
| **LFU Bucketing** | `Map<Integer, LinkedHashSet<K>>` | $O(1)$ operations with exact LRU tie-breaking. Minimum frequency tracker self-heals upon subsequent inserts without scanning all frequencies. |
| **TTL Expiration** | Lazy on-access + insert replacement | Zero background thread overhead by default; expired entries are evicted immediately on lookup or when encountered as eviction candidates. |
| **Real-time Streaming** | SSE (`SseEmitter(0L)`) with 500ms polling fallback | Lightweight uni-directional real-time push without WebSocket handshake complexity; automatically falls back if SSE is blocked by corporate proxies. |

---

## 📝 Assumptions

1. **Deterministic Pseudo-Random Traces**: Traces generated via `SplittableRandom(seed)` allow reproducible hit rates between runs.
2. **Key & Value Immutability**: Keys and values are non-null strings/objects.
3. **Compare Mode Threads**: When comparing LRU vs LFU, both simulations run concurrently with the configured thread count (capped at $2 \times \text{threads} \le 64$).
4. **Theme Preference**: Theme (Dark/Light) is maintained in React state without requiring persistent browser cookies.

---

## 🎬 Demo Walkthrough Script

1. **Preset 1: Zipfian Skew (LFU Dominance)**
   - Click the **Zipfian · LFU** preset (Capacity 100, KeySpace 1000, Skew 1.2).
   - Toggle **Compare LRU vs LFU** on.
   - Click **Run**. Observe both curves diverging: LFU stabilizes at ~66% while LRU hovers at ~57%. The **Verdict Banner** awards LFU the win.
2. **Preset 2: Sequential Scan (Cache Flushing)**
   - Select **Scan Cache Polluter**.
   - Click **Run**. Observe the hit rate plummet to ~0% as the large sequential key sweep flushes the working set out of the cache.
3. **Preset 3: TTL Churn**
   - Select **TTL Expiration Churn** (TTL = 500ms).
   - Click **Run**. Observe the **TTL Expirations** counter steadily climb alongside evictions.
4. **Interactive Control**
   - Click **Stop** mid-run; observe immediate transition to `STOPPED` state and graceful thread pool shutdown in < 500ms.
