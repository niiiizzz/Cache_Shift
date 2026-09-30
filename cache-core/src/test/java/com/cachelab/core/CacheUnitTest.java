package com.cachelab.core;

import com.cachelab.core.policy.Policy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.cachelab.core.Assertions.assertThat;
import static com.cachelab.core.Assertions.assertThatThrownBy;

class CacheUnitTest {

    @Test
    @DisplayName("AC-1: LRU eviction order - capacity 3, put A,B,C, get A, put D -> B is evicted")
    void testLruEvictionOrder() {
        Cache<String, String> cache = Cache.<String, String>builder()
                .capacity(3)
                .policy(Policy.LRU)
                .build();

        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");

        // Access A to make it MRU (order from MRU to LRU: A, C, B)
        assertThat(cache.get("A")).isEqualTo("1");

        // Put D triggers eviction of B (LRU)
        cache.put("D", "4");

        assertThat(cache.size()).isEqualTo(3);
        assertThat(cache.get("B")).isNull(); // Evicted
        assertThat(cache.get("A")).isEqualTo("1");
        assertThat(cache.get("C")).isEqualTo("3");
        assertThat(cache.get("D")).isEqualTo("4");

        assertThat(cache.stats().getEvictions()).isEqualTo(1L);
        assertThat(cache.stats().getHits()).isEqualTo(4L);
        assertThat(cache.stats().getMisses()).isEqualTo(1L);

        cache.checkInvariants();
    }

    @Test
    @DisplayName("AC-2: LFU eviction with LRU tie-break inside same frequency bucket")
    void testLfuEvictionWithTieBreak() {
        Cache<String, String> cache = Cache.<String, String>builder()
                .capacity(3)
                .policy(Policy.LFU)
                .build();

        // Initial inserts: A=1, B=1, C=1 (minFreq = 1, bucket 1 = [A, B, C])
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");

        // Access A 3 more times (freq = 4)
        cache.get("A");
        cache.get("A");
        cache.get("A");

        // Access B once (freq = 2), then C once (freq = 2)
        // Inside bucket freq=2, B was added first, C added second -> B is older (LRU)
        cache.get("B");
        cache.get("C");

        // Inserting D (freq 1) must evict B (freq 2, older than C)
        cache.put("D", "4");

        assertThat(cache.size()).isEqualTo(3);
        assertThat(cache.get("B")).isNull(); // Evicted
        assertThat(cache.get("A")).isEqualTo("1");
        assertThat(cache.get("C")).isEqualTo("3");
        assertThat(cache.get("D")).isEqualTo("4");

        assertThat(cache.stats().getEvictions()).isEqualTo(1L);
        cache.checkInvariants();
    }

    @Test
    @DisplayName("AC-3: TTL expiry independent of policy with fake ticker (including high-freq LFU entry)")
    void testTtlExpiryIndependentOfPolicy() {
        FakeTicker ticker = new FakeTicker(1_000_000_000L); // 1 sec base
        Cache<String, String> cache = Cache.<String, String>builder()
                .capacity(3)
                .policy(Policy.LFU)
                .ticker(ticker)
                .build();

        // Put A with 500 ms TTL
        cache.put("A", "valA", 500L);

        // Hammer A with accesses to increase frequency
        for (int i = 0; i < 100; i++) {
            assertThat(cache.get("A")).isEqualTo("valA");
        }
        assertThat(cache.stats().getHits()).isEqualTo(100L);
        assertThat(cache.stats().getExpirations()).isEqualTo(0L);

        // Advance ticker by 501 ms -> A has expired
        ticker.advanceMillis(501L);

        // Reading expired entry counts as miss + expiration and removes from both map and policy
        assertThat(cache.get("A")).isNull();
        assertThat(cache.stats().getMisses()).isEqualTo(1L);
        assertThat(cache.stats().getExpirations()).isEqualTo(1L);
        assertThat(cache.size()).isEqualTo(0);

        cache.checkInvariants();
    }

    @Test
    @DisplayName("AC-4: Re-put refreshes value & TTL and does not increase size")
    void testReputRefreshesTtlAndKeepsSize() {
        FakeTicker ticker = new FakeTicker(1_000_000_000L);
        Cache<String, String> cache = Cache.<String, String>builder()
                .capacity(2)
                .policy(Policy.LRU)
                .ticker(ticker)
                .build();

        cache.put("K1", "V1", 200L);
        assertThat(cache.size()).isEqualTo(1);

        // Advance 150 ms (still valid)
        ticker.advanceMillis(150L);
        assertThat(cache.get("K1")).isEqualTo("V1");

        // Re-put with new value and new 200 ms TTL
        cache.put("K1", "V1-Updated", 200L);
        assertThat(cache.size()).isEqualTo(1);

        // Advance another 100 ms (total 250 ms from original insert, but only 100 ms from re-put)
        ticker.advanceMillis(100L);
        assertThat(cache.get("K1")).isEqualTo("V1-Updated");

        // Advance 150 ms more -> expired
        ticker.advanceMillis(150L);
        assertThat(cache.get("K1")).isNull();
        assertThat(cache.stats().getExpirations()).isEqualTo(1L);

        cache.checkInvariants();
    }

    @Test
    @DisplayName("Capacity 1 edge case for both LRU and LFU")
    void testCapacityOne() {
        for (Policy policy : Policy.values()) {
            Cache<String, Integer> cache = Cache.<String, Integer>builder()
                    .capacity(1)
                    .policy(policy)
                    .build();

            cache.put("A", 1);
            assertThat(cache.get("A")).isEqualTo(1);
            assertThat(cache.size()).isEqualTo(1);

            cache.put("B", 2);
            assertThat(cache.size()).isEqualTo(1);
            assertThat(cache.get("A")).isNull();
            assertThat(cache.get("B")).isEqualTo(2);

            assertThat(cache.stats().getEvictions()).isEqualTo(1L);
            cache.checkInvariants();
        }
    }

    @Test
    @DisplayName("Expired victim on insert counts as expiration, not eviction")
    void testExpiredVictimOnInsertCountsAsExpiration() {
        FakeTicker ticker = new FakeTicker();
        Cache<String, String> cache = Cache.<String, String>builder()
                .capacity(2)
                .policy(Policy.LRU)
                .ticker(ticker)
                .build();

        cache.put("A", "valA", 100L); // Expires in 100ms
        cache.put("B", "valB", 0L);   // Never expires

        // Advance ticker by 150ms so A is expired, but not yet read
        ticker.advanceMillis(150L);

        // Cache is at capacity (2 entries in map). Inserting C will select A as victim (LRU)
        cache.put("C", "valC", 0L);

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("A")).isNull();
        assertThat(cache.get("B")).isEqualTo("valB");
        assertThat(cache.get("C")).isEqualTo("valC");

        // Victim was expired -> counted as expiration, NOT eviction!
        assertThat(cache.stats().getExpirations()).isEqualTo(1L);
        assertThat(cache.stats().getEvictions()).isEqualTo(0L);

        cache.checkInvariants();
    }

    @Test
    @DisplayName("Null key or null value throws NullPointerException")
    void testNullRejections() {
        Cache<String, String> cache = Cache.<String, String>builder()
                .capacity(10)
                .build();

        assertThatThrownBy(() -> cache.get(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> cache.put(null, "val")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> cache.put("key", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> cache.put(null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Sweep expired explicitly cleans all expired entries")
    void testSweepExpired() {
        FakeTicker ticker = new FakeTicker();
        Cache<String, String> cache = Cache.<String, String>builder()
                .capacity(10)
                .ticker(ticker)
                .build();

        cache.put("A", "1", 100L);
        cache.put("B", "2", 300L);
        cache.put("C", "3", 0L);

        ticker.advanceMillis(150L); // A expired, B and C valid
        int swept = cache.sweepExpired();

        assertThat(swept).isEqualTo(1);
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("A")).isNull();
        assertThat(cache.get("B")).isEqualTo("2");
        assertThat(cache.get("C")).isEqualTo("3");
        cache.checkInvariants();
    }
}
