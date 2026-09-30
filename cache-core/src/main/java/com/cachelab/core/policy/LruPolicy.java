package com.cachelab.core.policy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Doubly-linked list with sentinel head and tail + HashMap index for O(1) LRU eviction.
 * The most recently used (MRU) node is positioned right after head.
 * The least recently used (LRU) node is positioned right before tail.
 *
 * @param <K> key type
 */
public class LruPolicy<K> implements EvictionPolicy<K> {

    private static class Node<K> {
        K key;
        Node<K> prev;
        Node<K> next;

        Node(K key) {
            this.key = key;
        }
    }

    private final Map<K, Node<K>> index;
    private final Node<K> head;
    private final Node<K> tail;

    public LruPolicy() {
        this.index = new HashMap<>();
        this.head = new Node<>(null);
        this.tail = new Node<>(null);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public void onInsert(K key) {
        Node<K> existing = index.get(key);
        if (existing != null) {
            detach(existing);
            attachAfterHead(existing);
        } else {
            Node<K> node = new Node<>(key);
            index.put(key, node);
            attachAfterHead(node);
        }
    }

    @Override
    public void onAccess(K key) {
        Node<K> node = index.get(key);
        if (node != null) {
            detach(node);
            attachAfterHead(node);
        }
    }

    @Override
    public void onRemove(K key) {
        Node<K> node = index.remove(key);
        if (node != null) {
            detach(node);
        }
    }

    @Override
    public Optional<K> selectVictim() {
        if (index.isEmpty() || tail.prev == head) {
            return Optional.empty();
        }
        return Optional.of(tail.prev.key);
    }

    @Override
    public void clear() {
        index.clear();
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public Set<K> keys() {
        return Collections.unmodifiableSet(index.keySet());
    }

    private void attachAfterHead(Node<K> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void detach(Node<K> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }
}
