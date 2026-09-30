package com.cachelab.server.dto;

import com.cachelab.core.policy.Policy;
import com.cachelab.server.model.Pattern;
import com.cachelab.server.model.RunConfig;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class RunConfigRequest {

    private Policy policy = Policy.LRU;

    @Min(value = 1, message = "capacity must be at least 1")
    @Max(value = 1_000_000, message = "capacity must not exceed 1,000,000")
    private int capacity = 100;

    @Min(value = 1, message = "keySpace must be at least 1")
    @Max(value = 1_000_000, message = "keySpace must not exceed 1,000,000")
    private int keySpace = 1000;

    private Pattern pattern = Pattern.ZIPFIAN;

    @DecimalMin(value = "0.0", message = "zipfSkew must be >= 0.0")
    @DecimalMax(value = "3.0", message = "zipfSkew must be <= 3.0")
    private double zipfSkew = 1.0;

    @Min(value = 1, message = "totalOps must be at least 1")
    @Max(value = 5_000_000, message = "totalOps must not exceed 5,000,000")
    private int totalOps = 1_000_000;

    @Min(value = 1, message = "threads must be at least 1")
    @Max(value = 64, message = "threads must not exceed 64")
    private int threads = 8;

    @DecimalMin(value = "0.0", message = "readRatio must be >= 0.0")
    @DecimalMax(value = "1.0", message = "readRatio must be <= 1.0")
    private double readRatio = 0.9;

    @Min(value = 0, message = "ttlMs must be >= 0")
    private long ttlMs = 0L;

    @Min(value = 0, message = "loaderLatencyMs must be >= 0")
    @Max(value = 1000, message = "loaderLatencyMs must not exceed 1000")
    private long loaderLatencyMs = 1L;

    private long seed = 42L;

    public RunConfigRequest() {}

    public Policy getPolicy() {
        return policy != null ? policy : Policy.LRU;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getKeySpace() {
        return keySpace;
    }

    public void setKeySpace(int keySpace) {
        this.keySpace = keySpace;
    }

    public Pattern getPattern() {
        return pattern != null ? pattern : Pattern.ZIPFIAN;
    }

    public void setPattern(Pattern pattern) {
        this.pattern = pattern;
    }

    public double getZipfSkew() {
        return zipfSkew;
    }

    public void setZipfSkew(double zipfSkew) {
        this.zipfSkew = zipfSkew;
    }

    public int getTotalOps() {
        return totalOps;
    }

    public void setTotalOps(int totalOps) {
        this.totalOps = totalOps;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public double getReadRatio() {
        return readRatio;
    }

    public void setReadRatio(double readRatio) {
        this.readRatio = readRatio;
    }

    public long getTtlMs() {
        return ttlMs;
    }

    public void setTtlMs(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public long getLoaderLatencyMs() {
        return loaderLatencyMs;
    }

    public void setLoaderLatencyMs(long loaderLatencyMs) {
        this.loaderLatencyMs = loaderLatencyMs;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public RunConfig toDomain() {
        return new RunConfig(
                getPolicy(),
                capacity,
                keySpace,
                getPattern(),
                zipfSkew,
                totalOps,
                threads,
                readRatio,
                ttlMs,
                loaderLatencyMs,
                seed
        );
    }
}
