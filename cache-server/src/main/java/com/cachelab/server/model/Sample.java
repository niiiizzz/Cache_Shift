package com.cachelab.server.model;

/**
 * Metric sample record emitted periodically (every 200 ms) and on run completion.
 * Matches PRD Section 6 and 9 exact contract.
 */
public record Sample(
        String runId,
        Status status,
        long tMs,
        long opsCompleted,
        long totalOps,
        long hits,
        long misses,
        double hitRate,
        double missRate,
        double windowHitRate,
        long evictions,
        long expirations,
        int size,
        int capacity,
        double opsPerSec
) {}
