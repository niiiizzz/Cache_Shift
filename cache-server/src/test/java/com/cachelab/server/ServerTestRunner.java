package com.cachelab.server;

/**
 * Self-contained Test Runner for verifying CacheServer tests.
 */
public class ServerTestRunner {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  CacheLab Server Test Suite Execution");
        System.out.println("==================================================");

        int passed = 0;
        int failed = 0;

        TraceGeneratorTest traceTest = new TraceGeneratorTest();
        passed += runTest("AC-6 (Scan): Sequential Scan Collapses LRU", traceTest::testSequentialScanCollapsesLru);
        passed += runTest("AC-6 (Zipfian): LFU >= LRU on Zipfian", traceTest::testZipfianFavorsLfu);
        passed += runTest("Deterministic Seeded Trace", traceTest::testDeterministicSeededTrace);

        RunSimulationTest simTest = new RunSimulationTest();
        passed += runTest("Simulation Run Lifecycle (Workers + Sampler)", () -> {
            try {
                simTest.testRunExecutionCompletes();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        passed += runTest("AC-9: Stop Halts Run in < 1 Second", () -> {
            try {
                simTest.testStopHaltsWithinOneSecond();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        passed += runTest("Compare Mode Dual Runs Sharing Trace", simTest::testCompareMode);

        System.out.println("==================================================");
        System.out.println("  Server Summary: " + passed + " Passed, " + failed + " Failed");
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
