package com.cachelab.server.service;

import com.cachelab.core.Cache;
import com.cachelab.core.CacheStats;
import com.cachelab.server.model.RunConfig;
import com.cachelab.server.model.Sample;
import com.cachelab.server.model.Status;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages the lifecycle, multithreaded workers, sampling, and SSE streaming for a single simulation run.
 */
public class Run {

    private final String runId;
    private final RunConfig config;
    private final Cache<Integer, String> cache;
    private final int[] trace;
    private final AtomicLong opsCompleted;
    private final List<SseEmitter> emitters;
    private final AtomicBoolean finished;

    private volatile Status status;
    private volatile boolean cancelled;
    private volatile Sample latestSample;

    private ExecutorService workerPool;
    private ScheduledExecutorService samplerScheduler;
    private long startTimeNanos;

    private long prevHits = 0L;
    private long prevMisses = 0L;
    private long prevOps = 0L;
    private long prevTimeNanos = 0L;

    public Run(RunConfig config, int[] trace) {
        this(UUID.randomUUID().toString(), config, trace);
    }

    public Run(String runId, RunConfig config, int[] trace) {
        this.runId = runId;
        this.config = config;
        this.trace = trace;
        this.cache = Cache.<Integer, String>builder()
                .capacity(config.capacity())
                .policy(config.policy())
                .defaultTtlMs(config.ttlMs())
                .build();
        this.opsCompleted = new AtomicLong(0);
        this.emitters = new CopyOnWriteArrayList<>();
        this.status = Status.IDLE;
        this.cancelled = false;
        this.finished = new AtomicBoolean(false);
        this.latestSample = createInitialSample();
    }

    public synchronized void start() {
        if (status == Status.RUNNING) {
            return;
        }

        this.status = Status.RUNNING;
        this.startTimeNanos = System.nanoTime();
        this.prevTimeNanos = startTimeNanos;

        int threadCount = config.threads();
        this.workerPool = Executors.newFixedThreadPool(threadCount, new NamedThreadFactory("worker-" + runId.substring(0, 6)));
        this.samplerScheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("sampler-" + runId.substring(0, 6)));

        // Sampler scheduled task every 200 ms
        samplerScheduler.scheduleAtFixedRate(this::sampleAndBroadcast, 200, 200, TimeUnit.MILLISECONDS);

        // Slice trace into contiguous partitions
        int totalOps = trace.length;
        int sliceSize = (totalOps + threadCount - 1) / threadCount;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            final int startIdx = t * sliceSize;
            final int endIdx = Math.min(totalOps, startIdx + sliceSize);

            workerPool.submit(() -> {
                SplittableRandom threadRng = new SplittableRandom(config.seed() + threadIdx * 31L);
                long loaderLatency = config.loaderLatencyMs();
                double readRatio = config.readRatio();
                long ttlMs = config.ttlMs();

                try {
                    for (int i = startIdx; i < endIdx; i++) {
                        if (cancelled || Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        int key = trace[i];
                        boolean isRead = (threadRng.nextDouble() < readRatio);

                        if (isRead) {
                            String value = cache.get(key);
                            if (value == null) {
                                // Cache miss: simulate backend loader fetch outside the cache lock
                                if (loaderLatency > 0L) {
                                    Thread.sleep(loaderLatency);
                                }
                                cache.put(key, "Val-" + key, ttlMs);
                            }
                        } else {
                            // Direct write
                            cache.put(key, "Val-" + key, ttlMs);
                        }

                        opsCompleted.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t1) {
                    status = Status.FAILED;
                } finally {
                    latch.countDown();
                }
            });
        }

        // Monitoring thread awaiting worker pool completion
        Thread monitorThread = new Thread(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                completeRun();
            }
        }, "monitor-" + runId.substring(0, 6));
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public synchronized void stop() {
        if (finished.get()) {
            return;
        }
        cancelled = true;
        status = Status.STOPPED;
        if (workerPool != null) {
            workerPool.shutdownNow();
        }
        completeRun();
    }

    private synchronized void completeRun() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }

        if (status == Status.RUNNING) {
            status = cancelled ? Status.STOPPED : Status.DONE;
        }

        if (samplerScheduler != null) {
            samplerScheduler.shutdown();
        }
        if (workerPool != null) {
            workerPool.shutdown();
        }

        // Broadcast final complete sample
        Sample finalSample = buildCurrentSample(status);
        latestSample = finalSample;
        broadcast(finalSample);

        // Close SSE emitters
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
        emitters.clear();
    }

    public void addEmitter(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Replay latest sample immediately on subscribe
        try {
            emitter.send(SseEmitter.event()
                    .name("sample")
                    .data(latestSample));
            if (finished.get()) {
                emitter.complete();
            }
        } catch (IOException e) {
            emitters.remove(emitter);
        }
    }

    private synchronized void sampleAndBroadcast() {
        if (finished.get()) {
            return;
        }
        Sample sample = buildCurrentSample(status);
        latestSample = sample;
        broadcast(sample);
    }

    private void broadcast(Sample sample) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("sample")
                        .data(sample));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    private Sample buildCurrentSample(Status currentStatus) {
        long nowNanos = System.nanoTime();
        long completed = opsCompleted.get();
        CacheStats stats = cache.stats();
        long hits = stats.getHits();
        long misses = stats.getMisses();
        long evictions = stats.getEvictions();
        long expirations = stats.getExpirations();
        int size = cache.size();
        int capacity = cache.getCapacity();

        long tMs = Math.max(0L, (nowNanos - startTimeNanos) / 1_000_000L);
        double hitRate = stats.hitRate();
        double missRate = stats.missRate();

        // Windowed hit rate and ops/sec over interval
        long dHits = hits - prevHits;
        long dMisses = misses - prevMisses;
        long dTotalGets = dHits + dMisses;
        double windowHitRate = (dTotalGets == 0L) ? hitRate : (double) dHits / dTotalGets;

        long dOps = completed - prevOps;
        double dSeconds = Math.max(0.001, (nowNanos - prevTimeNanos) / 1_000_000_000.0);
        double opsPerSec = Math.max(0.0, dOps / dSeconds);

        prevHits = hits;
        prevMisses = misses;
        prevOps = completed;
        prevTimeNanos = nowNanos;

        return new Sample(
                runId,
                currentStatus,
                tMs,
                completed,
                trace.length,
                hits,
                misses,
                hitRate,
                missRate,
                windowHitRate,
                evictions,
                expirations,
                size,
                capacity,
                opsPerSec
        );
    }

    private Sample createInitialSample() {
        return new Sample(
                runId,
                Status.IDLE,
                0L,
                0L,
                trace != null ? trace.length : config.totalOps(),
                0L,
                0L,
                0.0,
                0.0,
                0.0,
                0L,
                0L,
                0,
                config.capacity(),
                0.0
        );
    }

    public String getRunId() {
        return runId;
    }

    public RunConfig getConfig() {
        return config;
    }

    public Status getStatus() {
        return status;
    }

    public Sample getLatestSample() {
        return latestSample;
    }

    public Cache<Integer, String> getCache() {
        return cache;
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        private final String name;
        DaemonThreadFactory(String name) { this.name = name; }
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicLong count = new AtomicLong(0);
        NamedThreadFactory(String prefix) { this.prefix = prefix; }
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
