package com.cachelab.server.model;

import java.util.Locale;

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
) {
    public String toJson() {
        return String.format(Locale.US,
                "{\"runId\":\"%s\",\"status\":\"%s\",\"tMs\":%d,\"opsCompleted\":%d,\"totalOps\":%d,\"hits\":%d,\"misses\":%d,\"hitRate\":%.4f,\"missRate\":%.4f,\"windowHitRate\":%.4f,\"evictions\":%d,\"expirations\":%d,\"size\":%d,\"capacity\":%d,\"opsPerSec\":%.2f}",
                runId, status != null ? status.name() : "IDLE", tMs, opsCompleted, totalOps, hits, misses, hitRate, missRate, windowHitRate, evictions, expirations, size, capacity, opsPerSec
        );
    }
}
