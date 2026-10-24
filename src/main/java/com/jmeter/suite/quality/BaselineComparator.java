package com.jmeter.suite.quality;

import com.jmeter.suite.config.EnvironmentConfig;
import com.jmeter.suite.report.ExecutionStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares completed plan metrics to a versioned, environment-specific baseline.
 */
public final class BaselineComparator {

    private static final Pattern NUMBER = Pattern.compile("\\\"%s\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private final Path baselinesDir;

    /** Creates a comparator using the repository baseline directory. */
    public BaselineComparator() {
        this(Paths.get("config", "baselines"));
    }

    /** Creates a comparator using the supplied directory, primarily for tests. */
    BaselineComparator(Path baselinesDir) {
        this.baselinesDir = baselinesDir;
    }

    /**
     * Returns human-readable regression reasons, or an empty list when the baseline passes.
     */
    public List<String> compare(String planId, EnvironmentConfig config, ExecutionStats current) {
        Path baseline = baselinesDir.resolve(config.name() + "-" + planId + ".json");
        if (!Files.exists(baseline)) {
            return List.of("baseline file missing: " + baseline);
        }
        try {
            String json = Files.readString(baseline);
            double tolerance = config.baselineTolerancePercent() / 100.0;
            List<String> failures = new ArrayList<>();
            requireNoIncrease(failures, "error rate", metric(json, "errorPct"), current.errorRatePercent(), tolerance);
            requireNoIncrease(failures, "p95 latency", metric(json, "p95ResponseTimeMs"),
                    current.p95ResponseTimeMs(), tolerance);
            requireNoDecrease(failures, "throughput", metric(json, "throughputTps"),
                    current.throughputTps(), tolerance);
            return failures;
        } catch (IOException ex) {
            return List.of("could not read baseline: " + ex.getMessage());
        }
    }

    private double metric(String json, String name) throws IOException {
        Matcher matcher = Pattern.compile(String.format(NUMBER.pattern(), Pattern.quote(name))).matcher(json);
        if (!matcher.find()) {
            throw new IOException("missing metric '" + name + "'");
        }
        return Double.parseDouble(matcher.group(1));
    }

    private void requireNoIncrease(List<String> failures, String name, double baseline, double current,
                                   double tolerance) {
        double allowed = baseline == 0 ? 0.01 : baseline * (1 + tolerance);
        if (current > allowed) {
            failures.add(name + " regressed: baseline=" + baseline + ", current=" + current);
        }
    }

    private void requireNoDecrease(List<String> failures, String name, double baseline, double current,
                                   double tolerance) {
        if (current < baseline * (1 - tolerance)) {
            failures.add(name + " regressed: baseline=" + baseline + ", current=" + current);
        }
    }
}
