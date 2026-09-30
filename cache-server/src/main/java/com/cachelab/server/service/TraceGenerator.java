package com.cachelab.server.service;

import com.cachelab.server.model.Pattern;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Deterministic trace generator for cache simulation workloads.
 */
public class TraceGenerator {

    /**
     * Generates a seeded key trace array based on pattern and parameters.
     *
     * @param pattern  the access distribution pattern
     * @param keySpace number of distinct keys (1..keySpace)
     * @param totalOps total number of operations to produce
     * @param zipfSkew skew parameter for Zipfian distribution (e.g. 1.0)
     * @param seed     random seed for reproducibility
     * @return int[] array of size totalOps with keys in [0, keySpace - 1]
     */
    public static int[] generateTrace(Pattern pattern, int keySpace, int totalOps, double zipfSkew, long seed) {
        if (keySpace <= 0 || totalOps <= 0) {
            throw new IllegalArgumentException("keySpace and totalOps must be positive");
        }

        int[] trace = new int[totalOps];
        SplittableRandom rng = new SplittableRandom(seed);

        switch (pattern) {
            case SEQUENTIAL_SCAN -> {
                for (int i = 0; i < totalOps; i++) {
                    trace[i] = i % keySpace;
                }
            }
            case UNIFORM -> {
                for (int i = 0; i < totalOps; i++) {
                    trace[i] = rng.nextInt(keySpace);
                }
            }
            case ZIPFIAN -> {
                double[] cdf = computeZipfianCdf(keySpace, zipfSkew);
                for (int i = 0; i < totalOps; i++) {
                    trace[i] = sampleZipfian(cdf, rng.nextDouble());
                }
            }
            case HOT_SET_SHIFT -> {
                double[] cdf = computeZipfianCdf(keySpace, zipfSkew);
                int half = totalOps / 2;
                int shift = keySpace / 2;
                for (int i = 0; i < totalOps; i++) {
                    int k = sampleZipfian(cdf, rng.nextDouble());
                    if (i >= half) {
                        k = (k + shift) % keySpace;
                    }
                    trace[i] = k;
                }
            }
        }

        return trace;
    }

    private static double[] computeZipfianCdf(int keySpace, double skew) {
        double[] cdf = new double[keySpace];
        double sum = 0.0;
        for (int i = 0; i < keySpace; i++) {
            int rank = i + 1;
            double weight = (skew == 0.0) ? 1.0 : 1.0 / Math.pow(rank, skew);
            sum += weight;
            cdf[i] = sum;
        }

        // Normalize to [0.0, 1.0]
        for (int i = 0; i < keySpace; i++) {
            cdf[i] /= sum;
        }
        cdf[keySpace - 1] = 1.0;
        return cdf;
    }

    private static int sampleZipfian(double[] cdf, double u) {
        int idx = Arrays.binarySearch(cdf, u);
        if (idx < 0) {
            idx = -idx - 1;
        }
        if (idx >= cdf.length) {
            idx = cdf.length - 1;
        }
        return idx;
    }
}
