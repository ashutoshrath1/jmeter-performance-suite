package com.jmeter.suite.report;

/**
 * Holds aggregate metrics derived from a JTL results file.
 */
public final class ExecutionStats {

    private final long sampleCount;
    private final long errorCount;
    private final long p95ResponseTimeMs;
    private final double throughputTps;

    /**
     * Creates immutable execution statistics.
     */
    public ExecutionStats(long sampleCount, long errorCount, long p95ResponseTimeMs, double throughputTps) {
        this.sampleCount = sampleCount;
        this.errorCount = errorCount;
        this.p95ResponseTimeMs = p95ResponseTimeMs;
        this.throughputTps = throughputTps;
    }

    /**
     * Returns total sample count.
     */
    public long sampleCount() {
        return sampleCount;
    }

    /**
     * Returns failed sample count.
     */
    public long errorCount() {
        return errorCount;
    }

    /**
     * Returns the 95th percentile response time in milliseconds.
     */
    public long p95ResponseTimeMs() {
        return p95ResponseTimeMs;
    }

    /**
     * Returns achieved throughput in samples per second across the sampling window.
     */
    public double throughputTps() {
        return throughputTps;
    }

    /**
     * Returns the failure percentage for the executed samples.
     */
    public double errorRatePercent() {
        if (sampleCount == 0) {
            return 100.0;
        }
        return (errorCount * 100.0) / sampleCount;
    }
}
