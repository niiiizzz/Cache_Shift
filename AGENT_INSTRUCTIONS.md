# AGENT_INSTRUCTIONS.md: Build Playbook for the Cache Prototype

**Role:** you are the implementing agent. Build the project exactly as specified, in the order below, verifying each gate before moving on.
**Time budget:** 3 hours total. Speed matters, but a working end-to-end demo matters more than completeness.

---

## 0. Read first (in this order)

Place all spec files in `/docs` in the repo and read them before writing code.

| Order | File | Use it for |
|---|---|---|
| 1 | `PRD.md` | **Source of truth for requirements**, API contract (Section 6), data model (Section 9), acceptance criteria (Section 11) |
| 2 | `TECH_STACK.md` | Exact dependencies, versions, scaffold commands, guardrails |
| 3 | `ARCHITECTURE.md` | Module boundaries, cache GET/PUT flows, LRU/LFU structures, run flow, failure behavior |
| 4 | `SYSTEM_DESIGN.md` | Rationale, edge-case table (6.2), limits and validation caps (7.2) |
| 5 | `UX_FLOW.md` | Screen layout, states, components, microcopy, color tokens, accessibility |

## 1. Precedence and overrides

1. If documents conflict: `PRD.md` > `TECH_STACK.md` > `ARCHITECTURE.md` > `SYSTEM_DESIGN.md` > `UX_FLOW.md`, **except** for the overrides below, which win over everything.
2. Do not ask questions. If something is unspecified, choose the simplest option that satisfies the PRD and note it in `README.md` under "Assumptions".

### Overrides (resolved decisions)

| Topic | Decision |
|---|---|
| Default `totalOps` | **1,000,000** (not 200,000), so runs last long enough for a readable chart |
| Other defaults | As in PRD Section 12 (`policy=LRU, capacity=100, keySpace=1000, pattern=ZIPFIAN, zipfSkew=1.0, threads=8, readRatio=0.9, ttlMs=0, loaderLatencyMs=1, seed=42`) |
| Compare mode threads | Each run gets the full configured `threads`; validation rejects compare requests where `threads * 2 > 64` |
| Validation caps | `capacity 1..1,000,000`, `keySpace 1..1,000,000`, `threads 1..64`, `totalOps 1..5,000,000`, `readRatio 0..1`, `ttlMs >= 0`, `loaderLatencyMs 0..1000`, `zipfSkew 0..3` |
| Compare trace | Generate the trace **once** and share the read-only `int[]` across both runs |
| Persistence of UI theme | In-memory React state only (no browser storage APIs) |
| Tier 2 items (sweeper, live entries table) | Build only if all Tier 0 and Tier 1 gates pass with time to spare |

## 2. Non-negotiable rules

1. `cache-core` imports **only JDK classes** (test scope: JUnit 5, AssertJ). No Spring, Lombok, or Guava. Run `grep -rn "org.springframework" cache-core/` after every phase; it must return nothing.
2. All cache state is guarded by **one `ReentrantLock`**, and `get` takes it too. Never sleep, load, log heavily, or call outside code while holding it.
3. All removals (eviction, expiry, explicit remove) go through **one private `removeInternal(key)`** that updates map and policy together.
4. Policies never read time. Expiry is decided in `Cache` using the injectable `LongSupplier` ticker (default `System.nanoTime`).
5. Expired-on-read counts as **miss + expiration**. An expired victim on insert counts as **expiration**, not eviction.
6. Metrics use `LongAdder`; the sampler reads them without taking the cache lock.
7. TTL tests use a fake ticker. **No `Thread.sleep` in core tests.**
8. Follow the JSON contract in `PRD.md` Section 6 exactly (field names, casing, enums, status values). The UI types in `types/api.ts` must mirror it.
9. Do not add dependencies beyond `TECH_STACK.md`. Do not add features beyond the PRD tiers.
10. Every background thread is daemon or explicitly shut down; Ctrl+C must exit the JVM cleanly.

## 3. Build sequence

Each phase has a **gate**. Do not start the next phase until the gate passes. Commit after every gate with the message shown.

### Phase 0: Scaffold (target 0:00-0:15)

**Tasks**
- [ ] Create the repo layout from `PRD.md` Section 10: parent `pom.xml`, modules `cache-core`, `cache-server`; `cache-ui` via Vite (`react-ts`).
- [ ] Java 17 in parent POM; `cache-server` uses Spring Boot 3.3.x web + validation + test starters.
- [ ] `cache-ui`: install `recharts lucide-react @fontsource/inter`, dev `tailwindcss@3.4 postcss autoprefixer`; configure Tailwind (content globs, `darkMode: 'class'`), paste tokens from `UX_FLOW.md` Section 9.6; configure Vite proxy `/api → http://localhost:8080`.
- [ ] Create `types/api.ts` mirroring PRD Section 9 records; create empty `EvictionPolicy` interface and `Policy`/`Pattern`/`Status` enums.

**Gate**
- `mvn -q install -DskipTests` succeeds; `npm run build` in `cache-ui` succeeds.
- Commit: `chore: scaffold modules`

### Phase 1: Cache core + tests (target 0:15-1:15) **[critical path]**

**Tasks**
- [ ] `EvictionPolicy<K>`, `LruPolicy` (sentinel head/tail doubly linked list + index map), `LfuPolicy` (freq map + `Map<Integer, LinkedHashSet<K>>` + `minFreq`; reset `minFreq = 1` on insert; on remove, leave `minFreq` to self-heal on next insert).
- [ ] `Cache<K,V>` + `CacheBuilder` per PRD FR-1..FR-8 and the semantics block; `CacheStats` with `LongAdder`s (`hits, misses, puts, evictions, expirations`); `hitRate()` guarded against divide-by-zero.
- [ ] Package-private `checkInvariants()`: `size <= capacity`, map keys == policy keys (add a `keys()` view on policies for this).
- [ ] Tests: PRD AC-1 (LRU order), AC-2 (LFU tie-break), AC-3 (TTL independent of policy, fake ticker), AC-4 (re-put refreshes TTL, size unchanged), capacity-1 edge case, expired-victim counting, null key/value rejection.
- [ ] Stress test (AC-5): 16 threads × ≥1M mixed ops on both policies, then `checkInvariants()` and `hits + misses == total gets`; overall timeout 60 s.

**Gate**
- `mvn -pl cache-core test` passes with all of the above.
- Grep rule (Section 2.1) passes.
- Commit: `feat(core): LRU/LFU cache with TTL, tests`

### Phase 2: Server + simulator (parallel with Phase 3; target 0:15-1:15 if multiple agents, else 1:15-1:50)

**Tasks**
- [ ] DTO records + validation (Section 1 caps) → `400 {"error": "..."}` via `@RestControllerAdvice`.
- [ ] `TraceGenerator`: `ZIPFIAN` (precomputed CDF + binary search), `SEQUENTIAL_SCAN`, `UNIFORM`, `HOT_SET_SHIFT`; `SplittableRandom(seed)`; returns `int[]`.
- [ ] `Run`: builds `Cache`, splits trace into contiguous slices, fixed worker pool. Worker per op: `SplittableRandom(seed + idx)` → `get` with prob `readRatio`; on miss `Thread.sleep(loaderLatencyMs)` **outside the lock** then `put(key, value, ttlMs)`; otherwise `put`. Check `cancelled` each op; update `AtomicLong opsCompleted`.
- [ ] `Sampler`: daemon scheduled task every 200 ms building `Sample` (cumulative + windowed rates, `opsPerSec`, `tMs`); store in `volatile latestSample`; push to emitters. After workers terminate, emit the final sample with `status=DONE` (or `STOPPED`/`FAILED`) and complete emitters.
- [ ] `RunManager`: `runId → Run` map (keep last 5), one active run (group); new start cancels the previous.
- [ ] Endpoints per PRD Section 6: `POST /api/runs`, `GET /api/runs/{id}/stream` (SSE event name `sample`, timeout 0, replay `latestSample` on subscribe), `POST /api/runs/{id}/stop`, `GET /api/runs/{id}`, `POST /api/compare` (Tier 1, do after Phase 4 if short on time).
- [ ] Server smoke test (`MockMvc`): valid config → `201`; invalid → `400`.

**Gate**
- Manual: `curl -N http://localhost:8080/api/runs/{id}/stream` prints ~5 samples/s with monotonically increasing `opsCompleted`, ends with `DONE`.
- Sample shows `hitRate` in [0,1], `hits + misses` consistent with gets, `size <= capacity`.
- Stop returns within 1 s.
- Commit: `feat(server): simulator, sampler, REST+SSE`

### Phase 3: UI (parallel with Phase 2; build against mock data first)

**Tasks**
- [ ] `hooks/useRunStream.ts`: `EventSource` on `/api/runs/{id}/stream`; on error fall back to polling `GET /api/runs/{id}` every 500 ms; keep last 300 samples; expose `{samples, latest, status, error}`.
- [ ] `api/client.ts`: `startRun`, `stopRun`, `getRun`, `compare` using `fetch` and relative URLs.
- [ ] Components per `UX_FLOW.md` Section 4: `ConfigPanel` (segmented policy control, conditional `zipfSkew`, lock while running, blur validation), presets row, Run/Stop button, three `KpiCard`s, `HitRateChart` (Recharts, `isAnimationActive={false}`, Y domain 0-1 percent, hit solid, miss dashed, stroke via `var(--hit)` / `var(--miss)`), `CounterGrid`, `StatusBar` + progress, `ErrorBanner`, theme toggle.
- [ ] Implement the state matrix (`UX_FLOW.md` Section 6): Idle, Running, Done, Stopped, Failed, Reconnecting.
- [ ] Tabular numerals for all metrics; focus rings; `aria-live` on status pill.

**Gate**
- With mock data: all six states render; chart stays smooth at 5 Hz.
- Commit: `feat(ui): dashboard with live chart and states`

### Phase 4: Integration (target 1:15-2:00)

**Tasks**
- [ ] Point the UI at the real backend; run the three presets end to end.
- [ ] Verify behaviors (with seed 42, capacity 100, keySpace 1000):
  - `ZIPFIAN`: LFU hit rate ≥ LRU hit rate.
  - `SEQUENTIAL_SCAN`: LRU hit rate near 0%.
  - `ttlMs=500`: expirations counter rises and hit rate drops.
- [ ] Verify Stop, invalid config (inline error), starting a new run mid-run, SSE reconnect (kill/restart the stream tab).
- [ ] Tune defaults if the chart is too sparse (Section 1 override already sets 1M ops).

**Gate**
- PRD AC-6..AC-10 and UX-1..UX-6 verified manually.
- Commit: `feat: end-to-end integration`

### Phase 5: Tier 1, compare mode (target 2:00-2:30)

**Tasks**
- [ ] `POST /api/compare`: creates two `Run`s (LRU, LFU), separate `Cache` instances, one shared trace; returns `{"runIds": {"LRU": "...", "LFU": "..."}}`; group cancellation.
- [ ] `CompareView`: subscribe to both streams; overlay two hit-rate lines (`--series-lru`, `--series-lfu`), labeled legend; `VerdictBanner` at completion ("LFU +12.4 pts hit rate on Zipfian"; tie if |delta| < 0.5 pts).
- [ ] Compare toggle disables the policy control.

**Gate**
- Compare on Zipfian shows two diverging lines and a verdict; Stop cancels both.
- Commit: `feat: compare mode`

### Phase 6: Freeze and polish (target 2:30-2:45)

**Tasks**
- [ ] **Feature freeze.** No new features after this point.
- [ ] `README.md`: what it is, how to run (two commands), architecture summary with the Mermaid diagram from `ARCHITECTURE.md` Section 2, trade-offs (global lock now, striping next), assumptions list, demo script (`UX_FLOW.md` Section 13).
- [ ] Optional throughput note: run the core stress harness with `loaderLatencyMs = 0` at 1/4/8/16 threads and record ops/s in the README (shows the global-lock ceiling honestly).
- [ ] Tier 2 only if time remains: TTL sweeper (daemon, 100 ms, uses `removeInternal`), live entries table.

**Gate**
- Fresh clone → `mvn -q install -DskipTests`, start server, `npm i && npm run dev` works with no extra steps.
- Commit: `docs: README, assumptions`

### Phase 7: Demo hardening (target 2:45-3:00)

- [ ] Run one warm-up run, then the demo script twice end to end.
- [ ] Confirm Ctrl+C exits the backend and no orphan threads/processes remain.
- [ ] Keep a screen recording as backup.

## 4. Cut order if behind schedule

Cut in this exact order, stopping when back on schedule:
1. Live entries table (FR-18)
2. TTL sweeper (FR-17)
3. Compare mode (FR-16)
4. `HOT_SET_SHIFT` pattern
5. Presets row
6. Dark-mode toggle

**Never cut:** Phase 1 tests (AC-1..AC-5), live hit/miss display, the policy selector, per-entry TTL, Stop, demo dry runs.

## 5. Definition of done

| # | Check |
|---|---|
| 1 | All PRD minimum requirements demonstrably work from the UI |
| 2 | `mvn test` passes (unit + stress + smoke) |
| 3 | Three presets produce the expected behaviors (Phase 4 gate) |
| 4 | UI shows live hit rate, miss rate, evictions, TTL expirations, progress, and status for a real multithreaded run |
| 5 | Stop works; invalid input shows an inline error; SSE loss falls back to polling |
| 6 | `cache-core` has zero framework imports |
| 7 | README explains run steps, architecture, trade-offs, assumptions |
| 8 | Demo script runs cleanly twice from a fresh start |

## 6. Common failure patterns (avoid)

| Mistake | Fix |
|---|---|
| Taking a read lock for `get` | `get` mutates policy state; use the exclusive lock |
| Leaving expired entries in the policy structure | Always call `removeInternal` |
| Counting expired reads as hits | They are misses (+ expiration) |
| Holding the lock during the loader sleep | Sleep in the worker, then call `put` |
| Emitting `DONE` before workers finish | Await worker termination, then final sample |
| SSE emitter timeout default (30 s) killing long runs | Use `new SseEmitter(0L)` |
| Chart animating each update | `isAnimationActive={false}` |
| Using Tailwind classes inside Recharts props | Use `var(--token)` strings |
| Tailwind v4 syntax | Pinned to 3.4 with `tailwind.config.js` |
| Running `mvn spring-boot:run` before installing `cache-core` | Run `mvn -q install -DskipTests` first |
| Non-daemon threads blocking JVM exit | Daemon sampler/sweeper; shut down pools on stop |

## 7. Progress reporting format

After each phase, output exactly:

```
PHASE <n> DONE
- Gate: <passed/failed + evidence, e.g. test counts, curl output summary>
- Deviations from spec: <none | list>
- Assumptions added to README: <none | list>
- Next: <phase n+1>
```

If a gate fails, fix it before proceeding; if blocked for more than ~10 minutes, apply the cut order in Section 4 and report the cut.
