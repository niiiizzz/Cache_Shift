package com.cachelab.core;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Deterministic fake time ticker for unit testing without Thread.sleep.
 */
public class FakeTicker implements LongSupplier {

    private final AtomicLong nanos;

    public FakeTicker() {
        this(0L);
    }

    public FakeTicker(long initialNanos) {
        this.nanos = new AtomicLong(initialNanos);
    }

    public void advanceMillis(long millis) {
        nanos.addAndGet(millis * 1_000_000L);
    }

    public void advanceNanos(long nanosToAdd) {
        nanos.addAndGet(nanosToAdd);
    }

    @Override
    public long getAsLong() {
        return nanos.get();
    }
}
