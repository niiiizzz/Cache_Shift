package com.cachelab.core;

import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe statistics counters for cache operations using LongAdder.
 * Counters can be sampled concurrently without taking the cache lock.
 */
public class CacheStats {

    public final LongAdder hits = new LongAdder();
    public final LongAdder misses = new LongAdder();
    public final LongAdder puts = new LongAdder();
    public final LongAdder evictions = new LongAdder();
    public final LongAdder expirations = new LongAdder();

    public long getHits() {
        return hits.sum();
    }

    public long getMisses() {
        return misses.sum();
    }

    public long getPuts() {
        return puts.sum();
    }

    public long getEvictions() {
        return evictions.sum();
    }

    public long getExpirations() {
        return expirations.sum();
    }

    public double hitRate() {
        long h = hits.sum();
        long m = misses.sum();
        long total = h + m;
        return total == 0L ? 0.0 : (double) h / total;
    }

    public double missRate() {
        long h = hits.sum();
        long m = misses.sum();
        long total = h + m;
        return total == 0L ? 0.0 : (double) m / total;
    }

    public void reset() {
        hits.reset();
        misses.reset();
        puts.reset();
        evictions.reset();
        expirations.reset();
    }
}
