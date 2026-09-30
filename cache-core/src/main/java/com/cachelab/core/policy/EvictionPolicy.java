package com.cachelab.core.policy;

import java.util.Optional;
import java.util.Set;

/**
 * Strategy interface for cache eviction policies.
 * Implementations track access/insertion recency or frequency.
 * Implementations never read system time; expiry is handled exclusively by the Cache container.
 *
 * @param <K> key type
 */
public interface EvictionPolicy<K> {

    /**
     * Called when a new key is inserted into the cache.
     *
     * @param key the inserted key
     */
    void onInsert(K key);

    /**
     * Called when an existing key is accessed or re-put.
     *
     * @param key the accessed key
     */
    void onAccess(K key);

    /**
     * Called when a key is removed from the cache (via eviction, expiration, or explicit remove).
     *
     * @param key the removed key
     */
    void onRemove(K key);

    /**
     * Selects a victim key for eviction when capacity is reached.
     *
     * @return an Optional containing the victim key, or empty if policy has no entries
     */
    Optional<K> selectVictim();

    /**
     * Clears all tracking state in the policy.
     */
    void clear();

    /**
     * Returns an unmodifiable or snapshot view of the keys currently tracked by the policy.
     * Used for cache invariant verification.
     *
     * @return set of tracked keys
     */
    Set<K> keys();
}
