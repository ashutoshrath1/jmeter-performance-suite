package com.jmeter.suite.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public enum PlanDefinition {
    BASELINE("baseline", "test-plans/baseline.jmx"),
    SPIKE("spike", "test-plans/spike-test.jmx"),
    STRESS("stress", "test-plans/stress-test.jmx"),
    ENDURANCE("endurance", "test-plans/endurance-test.jmx"),
    BREAKPOINT("breakpoint", "test-plans/breakpoint-test.jmx");

    private final String id;
    private final Path jmxPath;

    PlanDefinition(String id, String jmxPath) {
        this.id = id;
        this.jmxPath = Paths.get(jmxPath);
    }

    public String id() {
        return id;
    }

    public Path jmxPath() {
        return jmxPath;
    }

    public static List<PlanDefinition> resolveSuite(String suite) {
        String normalized = suite.toLowerCase();
        switch (normalized) {
            case "all":
                return Arrays.asList(values());
            case "quick":
                return Collections.singletonList(BASELINE);
            case "load":
                return Arrays.asList(BASELINE, STRESS);
            default:
                return byId(normalized)
                        .map(Collections::singletonList)
                        .orElse(Collections.emptyList());
        }
    }

    public static Optional<PlanDefinition> byId(String id) {
        return Arrays.stream(values())
                .filter(plan -> plan.id.equals(id))
                .findFirst();
    }

    public static String supportedSuites() {
        return "all, quick, load, " +
                Arrays.stream(values()).map(PlanDefinition::id).collect(Collectors.joining(", "));
    }
}
