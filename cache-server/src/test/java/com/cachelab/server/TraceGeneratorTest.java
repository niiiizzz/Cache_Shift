package com.cachelab.server;

import com.cachelab.core.Cache;
import com.cachelab.core.policy.Policy;
import com.cachelab.server.model.Pattern;
import com.cachelab.server.service.TraceGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TraceGeneratorTest {

    @Test
    @DisplayName("AC-6 (Scan): Sequential scan collapses LRU hit rate toward 0%")
    void testSequentialScanCollapsesLru() {
        int capacity = 50;
        int keySpace = 500;
        int totalOps = 50_000;

        int[] trace = TraceGenerator.generateTrace(Pattern.SEQUENTIAL_SCAN, keySpace, totalOps, 1.0, 42L);
        assertThat(trace).hasSize(totalOps);

        Cache<Integer, String> lruCache = Cache.<Integer, String>builder()
                .capacity(capacity)
                .policy(Policy.LRU)
                .build();

        for (int key : trace) {
            String val = lruCache.get(key);
            if (val == null) {
                lruCache.put(key, "V" + key);
            }
        }

        // Because keySpace (500) > capacity (50) and scan loops sequentially,
        // every single access is a miss after initial fill -> hitRate == 0.0
        assertThat(lruCache.stats().hitRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("AC-6 (Zipfian): LFU hit rate is at least LRU's on Zipfian distribution with same seed")
    void testZipfianFavorsLfu() {
        int capacity = 100;
        int keySpace = 1000;
        int totalOps = 100_000;
        long seed = 42L;

        int[] trace = TraceGenerator.generateTrace(Pattern.ZIPFIAN, keySpace, totalOps, 1.0, seed);

        Cache<Integer, String> lruCache = Cache.<Integer, String>builder()
                .capacity(capacity)
                .policy(Policy.LRU)
                .build();

        Cache<Integer, String> lfuCache = Cache.<Integer, String>builder()
                .capacity(capacity)
                .policy(Policy.LFU)
                .build();

        for (int key : trace) {
            if (lruCache.get(key) == null) {
                lruCache.put(key, "V" + key);
            }
            if (lfuCache.get(key) == null) {
                lfuCache.put(key, "V" + key);
            }
        }

        double lruHitRate = lruCache.stats().hitRate();
        double lfuHitRate = lfuCache.stats().hitRate();

        System.out.println("Zipfian comparison - LRU hit rate: " + lruHitRate + ", LFU hit rate: " + lfuHitRate);
        assertThat(lfuHitRate).isGreaterThanOrEqualTo(lruHitRate);
    }

    @Test
    @DisplayName("Deterministic trace generation produces identical array for same seed")
    void testDeterministicSeededTrace() {
        int[] trace1 = TraceGenerator.generateTrace(Pattern.ZIPFIAN, 1000, 10000, 1.0, 999L);
        int[] trace2 = TraceGenerator.generateTrace(Pattern.ZIPFIAN, 1000, 10000, 1.0, 999L);

        assertThat(Arrays.equals(trace1, trace2)).isTrue();
    }
}
