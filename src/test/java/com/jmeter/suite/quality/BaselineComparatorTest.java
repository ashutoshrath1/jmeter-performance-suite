package com.jmeter.suite.quality;

import com.jmeter.suite.config.EnvironmentConfig;
import com.jmeter.suite.report.ExecutionStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies baseline regression decisions. */
class BaselineComparatorTest {

    @Test
    void acceptsMetricsWithinTolerance(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("test-baseline.json"),
                "{\"errorPct\": 0.0, \"p95ResponseTimeMs\": 100, \"throughputTps\": 10}");
        Path environment = tempDir.resolve("env.properties");
        Files.writeString(environment, "baseline.tolerance.percent=10\n");

        EnvironmentConfig config = EnvironmentConfig.load("test", environment);
        BaselineComparator comparator = new BaselineComparator(tempDir);

        assertTrue(comparator.compare("baseline", config, new ExecutionStats(100, 0, 105, 9.5)).isEmpty());
    }

    @Test
    void reportsRegressedMetrics(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("test-baseline.json"),
                "{\"errorPct\": 0.0, \"p95ResponseTimeMs\": 100, \"throughputTps\": 10}");
        Path environment = tempDir.resolve("env.properties");
        Files.writeString(environment, "baseline.tolerance.percent=10\n");

        EnvironmentConfig config = EnvironmentConfig.load("test", environment);
        BaselineComparator comparator = new BaselineComparator(tempDir);

        assertFalse(comparator.compare("baseline", config, new ExecutionStats(100, 1, 120, 8)).isEmpty());
    }
}
