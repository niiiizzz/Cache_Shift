package com.cachelab.server;

import com.cachelab.core.policy.Policy;
import com.cachelab.server.dto.CompareRequest;
import com.cachelab.server.model.Pattern;
import com.cachelab.server.model.RunConfig;
import com.cachelab.server.model.Sample;
import com.cachelab.server.model.Status;
import com.cachelab.server.service.Run;
import com.cachelab.server.service.RunManager;
import com.cachelab.server.service.TraceGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunSimulationTest {

    @Test
    @DisplayName("Simulation run completes end to end with workers and sampler")
    void testRunExecutionCompletes() throws InterruptedException {
        RunConfig config = new RunConfig(
                Policy.LRU,
                100,
                1000,
                Pattern.ZIPFIAN,
                1.0,
                50_000,
                4,
                0.9,
                0L,
                0L, // 0ms latency for fast test execution
                42L
        );

        int[] trace = TraceGenerator.generateTrace(config.pattern(), config.keySpace(), config.totalOps(), config.zipfSkew(), config.seed());
        Run run = new Run(config, trace);
        run.start();

        // Await completion (up to 10 seconds)
        long start = System.currentTimeMillis();
        while (run.getStatus() == Status.RUNNING && (System.currentTimeMillis() - start) < 10_000L) {
            Thread.sleep(50);
        }

        assertThat(run.getStatus()).isEqualTo(Status.DONE);
        Sample sample = run.getLatestSample();
        assertThat(sample.status()).isEqualTo(Status.DONE);
        assertThat(sample.opsCompleted()).isEqualTo(50_000L);
        assertThat(sample.hits() + sample.misses()).isGreaterThan(0L);
        assertThat(sample.size()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("AC-9: Stop halts a running simulation within 1 second")
    void testStopHaltsWithinOneSecond() throws InterruptedException {
        RunConfig config = new RunConfig(
                Policy.LRU,
                100,
                1000,
                Pattern.ZIPFIAN,
                1.0,
                1_000_000,
                8,
                0.9,
                0L,
                10L, // 10ms simulated loader latency to keep workers active
                42L
        );

        int[] trace = TraceGenerator.generateTrace(config.pattern(), config.keySpace(), config.totalOps(), config.zipfSkew(), config.seed());
        Run run = new Run(config, trace);
        run.start();

        Thread.sleep(200); // Let it start
        assertThat(run.getStatus()).isEqualTo(Status.RUNNING);

        long stopStart = System.currentTimeMillis();
        run.stop();
        long stopDuration = System.currentTimeMillis() - stopStart;

        assertThat(run.getStatus()).isEqualTo(Status.STOPPED);
        assertThat(stopDuration).isLessThan(1000L); // Halts in < 1s
    }

    @Test
    @DisplayName("Compare mode executes dual runs sharing single trace")
    void testCompareMode() {
        RunManager runManager = new RunManager();
        CompareRequest req = new CompareRequest();
        req.setTotalOps(10_000);
        req.setLoaderLatencyMs(0L);

        Map<String, String> runIds = runManager.startCompare(req);
        assertThat(runIds).containsKeys("LRU", "LFU");

        Run lruRun = runManager.getRun(runIds.get("LRU"));
        Run lfuRun = runManager.getRun(runIds.get("LFU"));

        assertThat(lruRun).isNotNull();
        assertThat(lfuRun).isNotNull();

        runManager.stopAllActive();
        assertThat(lruRun.getStatus()).isEqualTo(Status.STOPPED);
        assertThat(lfuRun.getStatus()).isEqualTo(Status.STOPPED);
    }
}
