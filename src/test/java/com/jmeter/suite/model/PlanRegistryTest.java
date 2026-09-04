package com.jmeter.suite.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies plan discovery and suite resolution against a synthetic plan directory.
 */
class PlanRegistryTest {

    /**
     * Creates a plan directory containing empty JMX files with the given names.
     */
    private Path planDir(Path dir, String... filenames) throws IOException {
        Path plans = dir.resolve("test-plans");
        Files.createDirectories(plans);
        for (String name : filenames) {
            Files.write(plans.resolve(name), new byte[0]);
        }
        return plans;
    }

    /**
     * Writes a suite configuration file.
     */
    private Path suiteConfig(Path dir, String content) throws IOException {
        Path config = dir.resolve("suites.properties");
        Files.write(config, content.getBytes(StandardCharsets.UTF_8));
        return config;
    }

    /**
     * Reduces a resolved suite to its plan ids for readable assertions.
     */
    private List<String> ids(List<PlanDefinition> plans) {
        return plans.stream().map(PlanDefinition::id).collect(Collectors.toList());
    }

    @Test
    void derivesPlanIdsFromFilenames(@TempDir Path dir) throws IOException {
        Path plans = planDir(dir, "baseline.jmx", "stress-test.jmx", "spike-test.jmx");
        PlanRegistry registry = PlanRegistry.load(plans, dir.resolve("missing.properties"));

        assertEquals(List.of("baseline", "spike", "stress"), ids(registry.plans()),
                "the -test suffix and extension are dropped, and order is by id");
    }

    @Test
    void resolvesConfiguredSuitesInDeclaredOrder(@TempDir Path dir) throws IOException {
        Path plans = planDir(dir, "baseline.jmx", "stress-test.jmx", "spike-test.jmx");
        Path config = suiteConfig(dir, "quick=baseline\nload=baseline,stress\n");
        PlanRegistry registry = PlanRegistry.load(plans, config);

        assertEquals(List.of("baseline"), ids(registry.resolveSuite("quick")));
        assertEquals(List.of("baseline", "stress"), ids(registry.resolveSuite("load")));
    }

    @Test
    void allFallsBackToEveryDiscoveredPlanWhenNotConfigured(@TempDir Path dir) throws IOException {
        Path plans = planDir(dir, "baseline.jmx", "stress-test.jmx");
        PlanRegistry registry = PlanRegistry.load(plans, dir.resolve("missing.properties"));

        assertEquals(List.of("baseline", "stress"), ids(registry.resolveSuite("all")));
    }

    @Test
    void configuredAllOverridesDiscoveryOrder(@TempDir Path dir) throws IOException {
        Path plans = planDir(dir, "baseline.jmx", "stress-test.jmx", "spike-test.jmx");
        Path config = suiteConfig(dir, "all=spike,baseline,stress\n");
        PlanRegistry registry = PlanRegistry.load(plans, config);

        assertEquals(List.of("spike", "baseline", "stress"), ids(registry.resolveSuite("all")));
    }

    @Test
    void aNewPlanIsRunnableByIdWithoutConfiguration(@TempDir Path dir) throws IOException {
        // This is the point of discovery: dropping in a file is enough, no rebuild required.
        Path plans = planDir(dir, "baseline.jmx", "soak-test.jmx");
        Path config = suiteConfig(dir, "quick=baseline\n");
        PlanRegistry registry = PlanRegistry.load(plans, config);

        assertEquals(List.of("soak"), ids(registry.resolveSuite("soak")));
        assertTrue(registry.byId("soak").isPresent());
    }

    @Test
    void unknownSuiteResolvesToNothing(@TempDir Path dir) throws IOException {
        Path plans = planDir(dir, "baseline.jmx");
        PlanRegistry registry = PlanRegistry.load(plans, dir.resolve("missing.properties"));

        assertTrue(registry.resolveSuite("does-not-exist").isEmpty());
        assertFalse(registry.byId("does-not-exist").isPresent());
    }

    @Test
    void configuredMembersMissingFromDiskAreSkipped(@TempDir Path dir) throws IOException {
        Path plans = planDir(dir, "baseline.jmx");
        Path config = suiteConfig(dir, "load=baseline,stress\n");
        PlanRegistry registry = PlanRegistry.load(plans, config);

        assertEquals(List.of("baseline"), ids(registry.resolveSuite("load")),
                "a suite must not fail because one member was removed");
    }

    @Test
    void theShippedConfigurationResolvesEveryRealPlan() throws IOException {
        PlanRegistry registry = PlanRegistry.load();

        assertEquals(List.of("baseline", "spike", "stress", "endurance", "breakpoint"),
                ids(registry.resolveSuite("all")));
        assertEquals(List.of("baseline"), ids(registry.resolveSuite("quick")));
        assertEquals(List.of("baseline", "stress"), ids(registry.resolveSuite("load")));
    }
}
