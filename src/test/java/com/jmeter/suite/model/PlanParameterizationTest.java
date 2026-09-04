package com.jmeter.suite.model;

import org.apache.jmeter.util.JMeterUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that test plans expose their load profile and target host as overridable properties.
 */
class PlanParameterizationTest {

    /**
     * Initialises the minimal JMeter runtime needed to evaluate property functions.
     */
    @BeforeAll
    static void initJMeter() {
        JMeterUtils.setJMeterHome(Paths.get(".").toAbsolutePath().normalize().toString());
        JMeterUtils.loadJMeterProperties(Paths.get("config", "jmeter.properties").toString());
        JMeterUtils.initLocale();
    }

    /**
     * Reads a plan file as text.
     */
    private String readPlan(String planId) throws IOException {
        PlanDefinition plan = PlanRegistry.load().byId(planId)
                .orElseThrow(() -> new AssertionError("no such plan: " + planId));
        Path path = plan.jmxPath();
        assertTrue(Files.exists(path), "missing plan file: " + path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @CsvSource({
            "baseline,   50,  30,  5,  120",
            "spike,      200, 10,  5,  60",
            "stress,     100, 60,  10, 300",
            "endurance,  50,  60,  5,  1800",
            "breakpoint, 300, 600, 30, 60",
    })
    void loadProfileIsOverridablePerPlan(String planId, int targetLevel,
                                         int rampUp, int steps, int holdTime) throws IOException {
        String jmx = readPlan(planId);

        assertTrue(jmx.contains("${__P(" + planId + ".target_level," + targetLevel + ")}"),
                planId + " must expose target_level with its current value as the default");
        assertTrue(jmx.contains("${__P(" + planId + ".ramp_up," + rampUp + ")}"),
                planId + " must expose ramp_up");
        assertTrue(jmx.contains("${__P(" + planId + ".steps," + steps + ")}"),
                planId + " must expose steps");
        assertTrue(jmx.contains("${__P(" + planId + ".hold_time," + holdTime + ")}"),
                planId + " must expose hold_time");
    }

    @ParameterizedTest
    @CsvSource({"baseline", "spike", "stress", "endurance", "breakpoint"})
    void samplersReferenceHostAndProtocolVariables(String constant) throws IOException {
        String jmx = readPlan(constant);

        assertTrue(jmx.contains("<stringProp name=\"HTTPSampler.domain\">${host}</stringProp>"),
                constant + " samplers must resolve the domain from the host variable");
        assertTrue(jmx.contains("<stringProp name=\"HTTPSampler.protocol\">${protocol}</stringProp>"),
                constant + " samplers must resolve the protocol from the protocol variable");
        assertFalse(jmx.contains("<stringProp name=\"HTTPSampler.domain\">jsonplaceholder"),
                constant + " must not hardcode a target host on a sampler");
    }

    @Test
    void environmentConfigKeysMatchThePlanNamespace() throws IOException {
        // Guards against the previous state, where the environment files declared flat load keys
        // that no plan ever read.
        String dev = new String(Files.readAllBytes(
                Paths.get("config", "environments", "dev.properties")), StandardCharsets.UTF_8);

        for (String line : dev.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            String key = trimmed.substring(0, trimmed.indexOf('=')).trim();
            assertFalse(key.equals("target_level") || key.equals("ramp_up")
                            || key.equals("steps") || key.equals("hold_time"),
                    "flat load key '" + key + "' is read by no plan; use <plan>." + key);
        }
    }
}
