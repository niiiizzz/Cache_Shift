# Tech Stack: Cache Library + Live Metrics Panel

**Companion to:** `PRD.md` | **Rule:** use exactly this stack; add no dependency not listed here without a stated reason.
**Selection criteria:** fastest path to a working demo in 3 hours, minimal moving parts, zero external services.

---

## 1. Summary

| Layer | Choice | Version |
|---|---|---|
| Language (backend) | Java | 17 (LTS) |
| Build | Maven, multi-module | 3.9+ |
| Backend framework | Spring Boot (web only) | 3.3.x |
| Live transport | Server-Sent Events via `SseEmitter` | n/a |
| Cache core dependencies | **None** (JDK only) | n/a |
| Backend testing | JUnit 5 + AssertJ | via `spring-boot-starter-test` |
| Frontend | React + TypeScript + Vite | React 18, Vite 5 |
| Styling | Tailwind CSS | **3.4.x** (pin; v4 config differs) |
| Charts | Recharts | 2.x |
| Icons | lucide-react | latest |
| Font | Inter (via `@fontsource/inter`, no CDN) | n/a |
| Frontend state | React hooks + `EventSource`, no state library | n/a |
| Persistence / infra | None. No DB, Docker, or cloud | n/a |

## 2. Backend

### Modules

| Module | Dependencies allowed |
|---|---|
| `cache-core` | **JDK only.** Test scope: JUnit 5, AssertJ. No Spring, no Lombok, no Guava |
| `cache-server` | `cache-core`, `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test` (test) |

### Decisions

| Concern | Choice | Reason | Rejected |
|---|---|---|---|
| Web framework | Spring Boot web (Tomcat) | Agent familiarity, `SseEmitter` built in, validation, JSON via Jackson | Javalin/Micronaut (less agent-reliable), WebFlux (needless complexity) |
| Live push | SSE | One-way stream, auto-reconnect, no handshake code | WebSocket (bidirectional not needed), polling (jerky) |
| Concurrency in core | `ReentrantLock`, `HashMap`, `LinkedHashSet`, `LongAdder`, `LongSupplier` ticker | Correct and simple; all JDK | `ConcurrentHashMap` alone (no atomicity across map + policy), `synchronized` (no `tryLock`/fairness options) |
| Workers | `ExecutorService` fixed pool, sized to `threads` from config | Explicit lifecycle, easy stop | Virtual threads (would hide contention behavior in the demo), parallel streams |
| Sampler | `ScheduledExecutorService`, 200 ms, daemon thread | Matches PRD FR-11 | `@Scheduled` (tied to app lifecycle, hard to bind to runs) |
| TTL sweeper (Tier 2) | `ScheduledExecutorService`, 100 ms, daemon | Independent of policy | Timer/TimerTask |
| Zipfian sampling | Own implementation: precompute CDF over `keySpace`, binary search per draw | No dependency, reproducible with seed | commons-math3 (extra dependency) |
| RNG | `java.util.SplittableRandom` seeded per thread (`seed + threadIndex`) | Fast, deterministic per seed | `Math.random`, `ThreadLocalRandom` (not seedable) |
| DTOs | Java `record`s | Immutable, no Lombok | Lombok |
| Validation | Jakarta Bean Validation on `RunConfig` request DTO + explicit range checks | Returns clean `400` (PRD FR/AC-10) | Manual `if` chains only |
| Config | `application.properties`: `server.port=8080` | Minimal | YAML profiles |
| Logging | Spring default (Logback), INFO | Enough | Custom setups |
| CORS | Not needed in dev: Vite proxies `/api` to `:8080` | Avoids CORS bugs; add a permissive `CorsConfigurer` only as fallback | n/a |

### Parent POM essentials

- `<java.version>17</java.version>`, packaging `pom` with modules `cache-core`, `cache-server`.
- `cache-server` inherits Spring Boot via `spring-boot-starter-parent` (or `dependencyManagement` import).
- `cache-core` must build and test without Spring on the classpath. Verify with `mvn -pl cache-core test`.

## 3. Frontend

### Scaffold

```bash
npm create vite@latest cache-ui -- --template react-ts
cd cache-ui
npm i recharts lucide-react @fontsource/inter
npm i -D tailwindcss@3.4 postcss autoprefixer
npx tailwindcss init -p
```

### Dependencies (complete list)

| Package | Purpose |
|---|---|
| `react`, `react-dom` | UI |
| `recharts` | Live line chart (hit/miss), compare chart |
| `lucide-react` | Icons (Play, Square, Activity, etc.) |
| `@fontsource/inter` | Local font, works offline at the venue |
| `tailwindcss`, `postcss`, `autoprefixer` (dev) | Styling |

**Not used:** Redux/Zustand (one hook owns the stream), axios (use `fetch`), React Router (single page), shadcn/ui and component kits (extra setup time; build 6 small components by hand), CSS-in-JS, chart.js.

### Structure and conventions

```
cache-ui/src/
  api/       client.ts          (fetch wrappers: startRun, stopRun, getRun, compare)
  hooks/     useRunStream.ts    (EventSource lifecycle, sample buffer capped at 300, polling fallback)
  types/     api.ts             (TS mirrors of PRD Section 9 records)
  components/
    ConfigPanel.tsx  KpiCard.tsx  HitRateChart.tsx  CounterGrid.tsx
    StatusBar.tsx    CompareView.tsx
  App.tsx  main.tsx  index.css
```

- **TypeScript strict mode on.** Types in `types/api.ts` must match the JSON contract in `PRD.md` Section 6 exactly.
- `useRunStream(runId)` returns `{ samples, latest, status, error }`; on `EventSource` error it falls back to polling `GET /api/runs/{id}` every 500 ms.
- Chart: `LineChart` with `isAnimationActive={false}` for smooth 5 Hz updates; fixed Y domain `[0, 1]` formatted as percent.
- Render throttle: append at most one sample per SSE event; keep the last 300 points.

### Vite dev proxy (`vite.config.ts`)

```ts
server: { port: 5173, proxy: { '/api': 'http://localhost:8080' } }
```

With the proxy, SSE and REST use relative URLs (`/api/...`), so no CORS handling is needed.

### Tailwind setup

- `content: ['./index.html', './src/**/*.{ts,tsx}']`
- Extend theme with design tokens defined in the design system doc (colors, radius, shadows); use `font-family: Inter` as the default sans.
- Dark-mode strategy: `class` (optional toggle).

## 4. Testing

| Scope | Tool | What |
|---|---|---|
| Unit | JUnit 5 + AssertJ | LRU order, LFU tie-break, TTL with fake ticker, re-put semantics (PRD AC-1..AC-4) |
| Concurrency | JUnit 5 with `ExecutorService` + `CountDownLatch` | 16 threads, 1M ops, invariants (AC-5) |
| Policy behavior | JUnit 5 | Scan collapses LRU, Zipfian favors LFU with fixed seed (AC-6) |
| API smoke | `@SpringBootTest` + `MockMvc` (one test) | `POST /runs` valid and invalid config |
| Frontend | None. Manual check against the demo script | 3-hour budget |

## 5. Tooling and runtime

| Item | Choice |
|---|---|
| JDK | 17 |
| Node | 20 LTS |
| Package manager | npm |
| Run backend | `mvn -pl cache-server spring-boot:run` (after `mvn install -DskipTests` once) |
| Run frontend | `npm run dev` in `cache-ui` |
| Ports | Backend `8080`, UI `5173` |
| Formatting | None enforced (no time budget) |
| VCS | Git, single `main` branch, commit after each integrated milestone |

## 6. Performance and correctness guardrails (for the agent)

1. `cache-core` never imports from `org.springframework.*`. Enforce with a quick grep before each milestone.
2. Never call the loader or any sleep while holding the cache lock (the simulator sleeps in the worker, outside the cache).
3. SSE: one `SseEmitter` per subscriber, timeout `0` (no timeout), remove on completion/error, send the final `DONE` sample before completing.
4. All background threads (`sampler`, `sweeper`, workers) are daemon or shut down on run stop/app shutdown.
5. Use `System.nanoTime()` via the injected ticker; never `System.currentTimeMillis()` for TTL.
6. Metrics counters use `LongAdder`; the sampler computes windowed hit rate from counter deltas between samples.
7. Cap request limits server-side (PRD Section 6 errors) so a bad config can't hang the demo.

## 7. Explicitly out of the stack

Database, Redis, Kafka, Docker, Kubernetes, cloud deployment, auth libraries, GraphQL, WebSocket, Lombok, MapStruct, Micrometer/Prometheus/Grafana (mention as future work only), JMH (roadmap).
