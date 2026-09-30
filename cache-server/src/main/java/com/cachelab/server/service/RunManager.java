package com.cachelab.server.service;

import com.cachelab.core.policy.Policy;
import com.cachelab.server.dto.CompareRequest;
import com.cachelab.server.model.RunConfig;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RunManager {

    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private final List<Run> activeRunGroup = new CopyOnWriteArrayList<>();

    public synchronized String startRun(RunConfig config) {
        stopAllActive();

        int[] trace = TraceGenerator.generateTrace(
                config.pattern(),
                config.keySpace(),
                config.totalOps(),
                config.zipfSkew(),
                config.seed()
        );

        Run run = new Run(config, trace);
        runs.put(run.getRunId(), run);
        activeRunGroup.clear();
        activeRunGroup.add(run);

        trimHistory();
        run.start();
        return run.getRunId();
    }

    public synchronized Map<String, String> startCompare(CompareRequest request) {
        stopAllActive();

        int[] trace = TraceGenerator.generateTrace(
                request.getPattern(),
                request.getKeySpace(),
                request.getTotalOps(),
                request.getZipfSkew(),
                request.getSeed()
        );

        RunConfig lruConfig = new RunConfig(
                Policy.LRU,
                request.getCapacity(),
                request.getKeySpace(),
                request.getPattern(),
                request.getZipfSkew(),
                request.getTotalOps(),
                request.getThreads(),
                request.getReadRatio(),
                request.getTtlMs(),
                request.getLoaderLatencyMs(),
                request.getSeed()
        );

        RunConfig lfuConfig = new RunConfig(
                Policy.LFU,
                request.getCapacity(),
                request.getKeySpace(),
                request.getPattern(),
                request.getZipfSkew(),
                request.getTotalOps(),
                request.getThreads(),
                request.getReadRatio(),
                request.getTtlMs(),
                request.getLoaderLatencyMs(),
                request.getSeed()
        );

        Run runLru = new Run(lruConfig, trace);
        Run runLfu = new Run(lfuConfig, trace);

        runs.put(runLru.getRunId(), runLru);
        runs.put(runLfu.getRunId(), runLfu);

        activeRunGroup.clear();
        activeRunGroup.add(runLru);
        activeRunGroup.add(runLfu);

        trimHistory();
        runLru.start();
        runLfu.start();

        Map<String, String> result = new LinkedHashMap<>();
        result.put("LRU", runLru.getRunId());
        result.put("LFU", runLfu.getRunId());
        return result;
    }

    public Run getRun(String runId) {
        return runs.get(runId);
    }

    public synchronized void stopRun(String runId) {
        Run target = runs.get(runId);
        if (target != null) {
            target.stop();
        }
        // If part of compare group, stop entire active group
        if (activeRunGroup.stream().anyMatch(r -> r.getRunId().equals(runId))) {
            stopAllActive();
        }
    }

    public synchronized void stopAllActive() {
        for (Run r : activeRunGroup) {
            r.stop();
        }
        activeRunGroup.clear();
    }

    private void trimHistory() {
        if (runs.size() > 10) {
            runs.entrySet().removeIf(entry ->
                    !activeRunGroup.contains(entry.getValue()) && runs.size() > 5
            );
        }
    }
}
