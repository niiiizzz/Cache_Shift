package com.cachelab.core.policy;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Frequency map + LinkedHashSet frequency buckets for O(1) LFU eviction.
 * Inside the same frequency bucket, insertion order preserves recency (LRU tie-break).
 * New inserts reset minFreq to 1.
 *
 * @param <K> key type
 */
public class LfuPolicy<K> implements EvictionPolicy<K> {

    private final Map<K, Integer> freqOf;
    private final Map<Integer, LinkedHashSet<K>> buckets;
    private int minFreq;

    public LfuPolicy() {
        this.freqOf = new HashMap<>();
        this.buckets = new HashMap<>();
        this.minFreq = 1;
    }

    @Override
    public void onInsert(K key) {
        if (freqOf.containsKey(key)) {
            onAccess(key);
            return;
        }
        freqOf.put(key, 1);
        buckets.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
        minFreq = 1;
    }

    @Override
    public void onAccess(K key) {
        Integer f = freqOf.get(key);
        if (f == null) {
            onInsert(key);
            return;
        }

        LinkedHashSet<K> curBucket = buckets.get(f);
        if (curBucket != null) {
            curBucket.remove(key);
            if (curBucket.isEmpty() && f == minFreq) {
                minFreq++;
            }
        }

        int nextF = f + 1;
        freqOf.put(key, nextF);
        buckets.computeIfAbsent(nextF, k -> new LinkedHashSet<>()).add(key);
    }

    @Override
    public void onRemove(K key) {
        Integer f = freqOf.remove(key);
        if (f != null) {
            LinkedHashSet<K> b = buckets.get(f);
            if (b != null) {
                b.remove(key);
            }
            // minFreq self-heals on the next insert or victim search
        }
    }

    @Override
    public Optional<K> selectVictim() {
        if (freqOf.isEmpty()) {
            return Optional.empty();
        }

        while (!buckets.containsKey(minFreq) || buckets.get(minFreq) == null || buckets.get(minFreq).isEmpty()) {
            minFreq++;
        }

        LinkedHashSet<K> minBucket = buckets.get(minFreq);
        K victim = minBucket.iterator().next();
        return Optional.of(victim);
    }

    @Override
    public void clear() {
        freqOf.clear();
        buckets.clear();
        minFreq = 1;
    }

    @Override
    public Set<K> keys() {
        return Collections.unmodifiableSet(freqOf.keySet());
    }
}
