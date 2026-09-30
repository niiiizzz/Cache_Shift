package com.cachelab.core;

import com.cachelab.core.policy.Policy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.cachelab.core.Assertions.assertThat;

class CacheStressTest {

    @ParameterizedTest
    @EnumSource(Policy.class)
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("AC-5: 16-thread concurrency stress test with >= 1,000,000 operations")
    void testConcurrentStressAndInvariants(Policy policy) throws InterruptedException {
        int threads = 16;
        int opsPerThread = 65_000; // 16 * 65_000 = 1,040,000 ops total
        int capacity = 200;
        int keySpace = 1_000;

        Cache<Integer, String> cache = Cache.<Integer, String>builder()
                .capacity(capacity)
                .policy(policy)
                .build();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threads);
        AtomicLong totalGets = new AtomicLong(0);

        for (int t = 0; t < threads; t++) {
            final int threadIdx = t;
            pool.submit(() -> {
                SplittableRandom rng = new SplittableRandom(12345L + threadIdx);
                long localGets = 0;
                try {
                    startGate.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        int key = rng.nextInt(keySpace);
                        double action = rng.nextDouble();
                        if (action < 0.70) {
                            // 70% get
                            localGets++;
                            cache.get(key);
                        } else if (action < 0.95) {
                            // 25% put
                            cache.put(key, "Val-" + key);
                        } else {
                            // 5% remove
                            cache.remove(key);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    totalGets.addAndGet(localGets);
                    finishGate.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startGate.countDown();
        boolean completed = finishGate.await(50, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(completed).as("All stress workers finished").isTrue();

        // Check Hard Invariants
        cache.checkInvariants();
        assertThat(cache.size()).isLessThanOrEqualTo(capacity);

        CacheStats stats = cache.stats();
        long recordedGets = stats.getHits() + stats.getMisses();
        assertThat(recordedGets).isEqualTo(totalGets.get());
        assertThat(stats.getPuts()).isGreaterThan(0L);
    }
}
