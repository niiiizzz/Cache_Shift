package com.cachelab.core;

import com.cachelab.core.policy.Policy;

/**
 * Self-contained Test Runner for verifying CacheCore unit tests and stress tests.
 */
public class CacheCoreTestRunner {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  CacheLab Core Test Suite Execution");
        System.out.println("==================================================");

        int passed = 0;
        int failed = 0;

        // Run Unit Tests
        CacheUnitTest unitTest = new CacheUnitTest();

        passed += runTest("AC-1: LRU Eviction Order", unitTest::testLruEvictionOrder);
        passed += runTest("AC-2: LFU Eviction with LRU Tie-break", unitTest::testLfuEvictionWithTieBreak);
        passed += runTest("AC-3: TTL Independent of Policy (FakeTicker)", unitTest::testTtlExpiryIndependentOfPolicy);
        passed += runTest("AC-4: Re-put Refreshes TTL and Size Unchanged", unitTest::testReputRefreshesTtlAndKeepsSize);
        passed += runTest("Capacity 1 Edge Case (LRU + LFU)", unitTest::testCapacityOne);
        passed += runTest("Expired Victim on Insert Counts as Expiration", unitTest::testExpiredVictimOnInsertCountsAsExpiration);
        passed += runTest("Null Key/Value Rejection", unitTest::testNullRejections);
        passed += runTest("Active Sweep Expired", unitTest::testSweepExpired);

        // Run Stress Tests
        CacheStressTest stressTest = new CacheStressTest();
        passed += runTest("AC-5: 16-Thread Stress Test (LRU, 1.04M ops)", () -> {
            try {
                stressTest.testConcurrentStressAndInvariants(Policy.LRU);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        passed += runTest("AC-5: 16-Thread Stress Test (LFU, 1.04M ops)", () -> {
            try {
                stressTest.testConcurrentStressAndInvariants(Policy.LFU);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println("==================================================");
        System.out.println("  Summary: " + passed + " Passed, " + failed + " Failed");
        System.out.println("==================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static int runTest(String name, Runnable test) {
        try {
            long start = System.currentTimeMillis();
            test.run();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("[PASS] " + name + " (" + elapsed + " ms)");
            return 1;
        } catch (Throwable t) {
            System.err.println("[FAIL] " + name + ": " + t.getMessage());
            t.printStackTrace(System.err);
            return 0;
        }
    }
}
