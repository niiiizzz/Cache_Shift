package com.cachelab.core;

import com.cachelab.core.policy.EvictionPolicy;
import com.cachelab.core.policy.LfuPolicy;
import com.cachelab.core.policy.LruPolicy;
import com.cachelab.core.policy.Policy;

import java.util.function.LongSupplier;

/**
 * Fluent builder for Cache instances.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class CacheBuilder<K, V> {

    private int capacity = 100;
    private Policy policyType = Policy.LRU;
    private EvictionPolicy<K> customPolicy = null;
    private LongSupplier ticker = System::nanoTime;
    private long defaultTtlMs = 0L;

    public CacheBuilder<K, V> capacity(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1, got " + capacity);
        }
        this.capacity = capacity;
        return this;
    }

    public CacheBuilder<K, V> policy(Policy policyType) {
        this.policyType = policyType;
        this.customPolicy = null;
        return this;
    }

    public CacheBuilder<K, V> policy(EvictionPolicy<K> customPolicy) {
        this.customPolicy = customPolicy;
        return this;
    }

    public CacheBuilder<K, V> ticker(LongSupplier ticker) {
        this.ticker = ticker;
        return this;
    }

    public CacheBuilder<K, V> defaultTtlMs(long defaultTtlMs) {
        this.defaultTtlMs = defaultTtlMs;
        return this;
    }

    public Cache<K, V> build() {
        EvictionPolicy<K> policyInstance;
        if (customPolicy != null) {
            policyInstance = customPolicy;
        } else if (policyType == Policy.LFU) {
            policyInstance = new LfuPolicy<>();
        } else {
            policyInstance = new LruPolicy<>();
        }

        return new Cache<>(capacity, policyInstance, ticker, defaultTtlMs);
    }
}
