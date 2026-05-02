package com.jmeter.suite.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Enumerates supported JMeter plans and suite-resolution rules.
 */
public enum PlanDefinition {
    BASELINE("baseline", "test-plans/baseline.jmx"),
    SPIKE("spike", "test-plans/spike-test.jmx"),
    STRESS("stress", "test-plans/stress-test.jmx"),
    ENDURANCE("endurance", "test-plans/endurance-test.jmx"),
    BREAKPOINT("breakpoint", "test-plans/breakpoint-test.jmx");

    private final String id;
    private final Path jmxPath;

    /**
     * Creates a plan definition with identifier and JMX path.
     */
    PlanDefinition(String id, String jmxPath) {
        this.id = id;
        this.jmxPath = Paths.get(jmxPath);
    }

    /**
     * Returns the stable plan identifier.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the filesystem path to the plan JMX.
     */
    public Path jmxPath() {
        return jmxPath;
    }

    /**
     * Resolves a suite token to an ordered list of plan definitions.
     */
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

    /**
     * Returns a plan definition for the provided id when available.
     */
    public static Optional<PlanDefinition> byId(String id) {
        return Arrays.stream(values())
                .filter(plan -> plan.id.equals(id))
                .findFirst();
    }

    /**
     * Returns a user-facing list of all supported suite and plan names.
     */
    public static String supportedSuites() {
        return "all, quick, load, " +
                Arrays.stream(values()).map(PlanDefinition::id).collect(Collectors.joining(", "));
    }
}
