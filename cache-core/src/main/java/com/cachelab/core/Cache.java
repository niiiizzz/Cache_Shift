package com.cachelab.core;

import com.cachelab.core.policy.EvictionPolicy;
import com.cachelab.core.policy.LfuPolicy;
import com.cachelab.core.policy.LruPolicy;
import com.cachelab.core.policy.Policy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * Thread-safe, in-memory cache supporting LRU and LFU eviction policies,
 * per-entry TTL independent of eviction, and lock-free stats sampling.
 *
 * All cache state mutations and lookups are guarded by a single ReentrantLock.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class Cache<K, V> {

    private final int capacity;
    private final Map<K, Entry<K, V>> map;
    private final EvictionPolicy<K> policy;
    private final ReentrantLock lock;
    private final LongSupplier ticker;
    private final CacheStats stats;
    private final long defaultTtlMs;

    Cache(int capacity, EvictionPolicy<K> policy, LongSupplier ticker, long defaultTtlMs) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Cache capacity must be >= 1, got " + capacity);
        }
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.policy = Objects.requireNonNull(policy, "EvictionPolicy must not be null");
        this.ticker = Objects.requireNonNull(ticker, "Ticker must not be null");
        this.defaultTtlMs = Math.max(0L, defaultTtlMs);
        this.lock = new ReentrantLock();
        this.stats = new CacheStats();
    }

    public static <K, V> CacheBuilder<K, V> builder() {
        return new CacheBuilder<>();
    }

    /**
     * Looks up an entry by key.
     * Note: get() acquires the exclusive lock because it mutates eviction policy state
     * (e.g. access order for LRU, frequency for LFU) and may trigger lazy expiry removal.
     *
     * @param key the lookup key
     * @return the cached value, or null if missing or expired
     */
    public V get(K key) {
        Objects.requireNonNull(key, "Key must not be null");
        lock.lock();
        try {
            Entry<K, V> entry = map.get(key);
            if (entry == null) {
                stats.misses.increment();
                return null;
            }

            long now = ticker.getAsLong();
            if (entry.isExpired(now)) {
                removeInternal(key);
                stats.expirations.increment();
                stats.misses.increment();
                return null;
            }

            policy.onAccess(key);
            stats.hits.increment();
            return entry.getValue();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Puts a key-value pair using the cache's default TTL.
     */
    public void put(K key, V value) {
        put(key, value, defaultTtlMs);
    }

    /**
     * Puts a key-value pair with a custom per-entry TTL in milliseconds (0 = never expires).
     */
    public void put(K key, V value, long ttlMs) {
        Objects.requireNonNull(key, "Key must not be null");
        Objects.requireNonNull(value, "Value must not be null");

        lock.lock();
        try {
            long now = ticker.getAsLong();
            long expiresAtNanos = (ttlMs > 0L) ? now + (ttlMs * 1_000_000L) : 0L;

            Entry<K, V> existing = map.get(key);
            if (existing != null) {
                // Re-put refreshes value & TTL, keeps LFU frequency, updates recency in LRU
                existing.setValue(value);
                existing.setExpiresAtNanos(expiresAtNanos);
                policy.onAccess(key);
                stats.puts.increment();
                return;
            }

            // New key insertion
            if (map.size() >= capacity) {
                Optional<K> victimOpt = policy.selectVictim();
                if (victimOpt.isPresent()) {
                    K victimKey = victimOpt.get();
                    Entry<K, V> victimEntry = map.get(victimKey);
                    boolean expired = (victimEntry != null && victimEntry.isExpired(now));
                    removeInternal(victimKey);
                    if (expired) {
                        stats.expirations.increment();
                    } else {
                        stats.evictions.increment();
                    }
                }
            }

            Entry<K, V> newEntry = new Entry<>(key, value, expiresAtNanos);
            map.put(key, newEntry);
            policy.onInsert(key);
            stats.puts.increment();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Explicitly removes a key from the cache.
     *
     * @param key the key to remove
     * @return the previous value, or null if not found
     */
    public V remove(K key) {
        if (key == null) {
            return null;
        }
        lock.lock();
        try {
            Entry<K, V> entry = map.get(key);
            if (entry != null) {
                removeInternal(key);
                return entry.getValue();
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Single choke point for all removals (eviction, expiry, explicit removal).
     * Must be called while holding the lock.
     */
    private void removeInternal(K key) {
        map.remove(key);
        policy.onRemove(key);
    }

    /**
     * Reclaims expired entries actively (used by background sweeper or maintenance).
     *
     * @return number of entries evicted due to expiration
     */
    public int sweepExpired() {
        lock.lock();
        try {
            long now = ticker.getAsLong();
            int count = 0;
            // Iterate over snapshot array to safely remove during traversal
            for (Map.Entry<K, Entry<K, V>> e : new HashMap<>(map).entrySet()) {
                if (e.getValue().isExpired(now)) {
                    removeInternal(e.getKey());
                    stats.expirations.increment();
                    count++;
                }
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public void clear() {
        lock.lock();
        try {
            map.clear();
            policy.clear();
        } finally {
            lock.unlock();
        }
    }

    public CacheStats stats() {
        return stats;
    }

    /**
     * Package-private invariant check for testing and verification.
     * Asserts that:
     * 1. size <= capacity
     * 2. map keys exactly equal policy keys
     */
    void checkInvariants() {
        lock.lock();
        try {
            if (map.size() > capacity) {
                throw new IllegalStateException("Invariant violated: size (" + map.size() + ") > capacity (" + capacity + ")");
            }
            if (!map.keySet().equals(policy.keys())) {
                throw new IllegalStateException("Invariant violated: map keys (" + map.keySet() + ") != policy keys (" + policy.keys() + ")");
            }
        } finally {
            lock.unlock();
        }
    }
}
