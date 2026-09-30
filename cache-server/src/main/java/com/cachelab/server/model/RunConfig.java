package com.cachelab.server.model;

import com.cachelab.core.policy.Policy;

/**
 * Domain record holding configuration parameters for a cache simulation run.
 */
public record RunConfig(
        Policy policy,
        int capacity,
        int keySpace,
        Pattern pattern,
        double zipfSkew,
        int totalOps,
        int threads,
        double readRatio,
        long ttlMs,
        long loaderLatencyMs,
        long seed
) {
    public static RunConfig defaultLru() {
        return new RunConfig(
                Policy.LRU,
                100,
                1000,
                Pattern.ZIPFIAN,
                1.0,
                1_000_000,
                8,
                0.9,
                0L,
                1L,
                42L
        );
    }
}
