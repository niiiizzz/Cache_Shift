package com.cachelab.core;

/**
 * Cache entry holding key, value, and absolute expiration timestamp in nanoseconds.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class Entry<K, V> {

    private final K key;
    private V value;
    private long expiresAtNanos;

    public Entry(K key, V value, long expiresAtNanos) {
        this.key = key;
        this.value = value;
        this.expiresAtNanos = expiresAtNanos;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }

    public long getExpiresAtNanos() {
        return expiresAtNanos;
    }

    public void setExpiresAtNanos(long expiresAtNanos) {
        this.expiresAtNanos = expiresAtNanos;
    }

    public boolean isExpired(long nowNanos) {
        return expiresAtNanos != 0L && nowNanos >= expiresAtNanos;
    }
}
