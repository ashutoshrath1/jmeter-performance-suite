package com.jmeter.suite.metrics;

import com.jmeter.suite.config.EnvironmentConfig;
import org.apache.jmeter.config.Argument;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.visualizers.backend.BackendListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies backend-listener construction from environment configuration.
 */
class BackendListenerFactoryTest {

    /**
     * Writes an environment file and loads it.
     */
    private EnvironmentConfig config(Path dir, String content) throws IOException {
        Path file = dir.resolve("test.properties");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return EnvironmentConfig.load("test", file);
    }

    /**
     * Flattens the listener's arguments into a plain map.
     */
    private Map<String, String> argumentsOf(BackendListener listener) {
        Map<String, String> values = new HashMap<>();
        for (JMeterProperty property : listener.getArguments()) {
            Argument argument = (Argument) property.getObjectValue();
            values.put(argument.getName(), argument.getValue());
        }
        return values;
    }

    @Test
    void disabledByDefault(@TempDir Path dir) throws IOException {
        assertFalse(BackendListenerFactory.create(config(dir, "host=example.com\n"), "baseline")
                .isPresent(), "metric streaming must be opt-in");
    }

    @Test
    void explicitlyDisabledYieldsNoListener(@TempDir Path dir) throws IOException {
        assertFalse(BackendListenerFactory.create(config(dir, "metrics.enabled=false\n"), "baseline")
                .isPresent());
    }

    @Test
    void buildsListenerWithConfiguredClientAndArguments(@TempDir Path dir) throws IOException {
        EnvironmentConfig cfg = config(dir,
                "metrics.enabled=true\n"
                        + "metrics.classname=com.example.MyClient\n"
                        + "metrics.queue.size=250\n"
                        + "metrics.arg.influxdbUrl=http://localhost:8086/write?db=jmeter\n"
                        + "metrics.arg.application=checkout-api\n");

        Optional<BackendListener> created = BackendListenerFactory.create(cfg, "stress");

        assertTrue(created.isPresent());
        BackendListener listener = created.get();
        assertEquals("com.example.MyClient", listener.getClassname());
        assertEquals("250", listener.getQueueSize());

        Map<String, String> args = argumentsOf(listener);
        assertEquals("http://localhost:8086/write?db=jmeter", args.get("influxdbUrl"));
        assertEquals("checkout-api", args.get("application"),
                "a configured application must not be overwritten by the default");
    }

    @Test
    void defaultsIdentifyingTagsSoRunsStayDistinguishable(@TempDir Path dir) throws IOException {
        EnvironmentConfig cfg = config(dir, "metrics.enabled=true\n");

        BackendListener listener = BackendListenerFactory.create(cfg, "endurance").orElseThrow();

        Map<String, String> args = argumentsOf(listener);
        assertEquals("jmeter-performance-suite", args.get("application"));
        assertEquals("jmeter", args.get("measurement"));
        assertEquals("endurance", args.get("testTitle"));
        assertEquals("plan=endurance,environment=test", args.get("eventTags"),
                "plan and environment must be tagged so one backend can hold many runs");
        assertTrue(listener.getClassname().endsWith("InfluxdbBackendListenerClient"),
                "InfluxDB is the default client");
    }
}
