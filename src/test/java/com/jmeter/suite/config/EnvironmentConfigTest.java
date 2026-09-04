package com.jmeter.suite.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies environment property resolution, including values that were previously read by nothing.
 */
class EnvironmentConfigTest {

    /**
     * Writes an environment file and loads it.
     */
    private EnvironmentConfig config(Path dir, String content) throws IOException {
        Path file = dir.resolve("env.properties");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return EnvironmentConfig.load("test", file);
    }

    @Test
    void buildsBaseUrlOmittingDefaultPorts(@TempDir Path dir) throws IOException {
        assertEquals("https://api.example.com",
                config(dir, "protocol=https\nhost=api.example.com\nport=443\n").baseUrl());
        assertEquals("http://api.example.com",
                config(dir, "protocol=http\nhost=api.example.com\nport=80\n").baseUrl());
        assertEquals("http://127.0.0.1:8099",
                config(dir, "protocol=http\nhost=127.0.0.1\nport=8099\n").baseUrl(),
                "a non-default port must appear, or the health check probes the wrong port");
        assertEquals("https://api.example.com",
                config(dir, "protocol=https\nhost=api.example.com\n").baseUrl());
    }

    @Test
    void p95GateAcceptsTheLegacyKeyAsAnAlias(@TempDir Path dir) throws IOException {
        assertEquals(1500, config(dir, "p95_response_time_ms=1500\n").p95ResponseTimeMs());
        assertEquals(2000, config(dir, "max_response_time_ms=2000\n").p95ResponseTimeMs(),
                "existing environment files using the old key must keep working");
        assertEquals(1500, config(dir, "p95_response_time_ms=1500\nmax_response_time_ms=9999\n")
                .p95ResponseTimeMs(), "the explicit key wins over the alias");
        assertEquals(0, config(dir, "host=x\n").p95ResponseTimeMs(), "unset means ungated");
    }

    @Test
    void throughputGateDefaultsToUngated(@TempDir Path dir) throws IOException {
        assertEquals(20.0, config(dir, "min_throughput_tps=20\n").minThroughputTps(), 0.001);
        assertEquals(0.0, config(dir, "host=x\n").minThroughputTps(), 0.001);
    }

    @Test
    void remoteHostsAreEmptyUnlessConfigured(@TempDir Path dir) throws IOException {
        assertTrue(config(dir, "host=x\n").remoteHosts().isEmpty(),
                "no remote hosts means run in this JVM");
        assertTrue(config(dir, "remote.hosts=\n").remoteHosts().isEmpty());
    }

    @Test
    void remoteHostsAreParsedAndTrimmed(@TempDir Path dir) throws IOException {
        List<String> hosts = config(dir, "remote.hosts= 10.0.0.1:1099 , 10.0.0.2:1099 ,\n")
                .remoteHosts();

        assertEquals(List.of("10.0.0.1:1099", "10.0.0.2:1099"), hosts);
    }

    @Test
    void prefixedPropertiesAreCollectedForPassThrough(@TempDir Path dir) throws IOException {
        Map<String, String> args = config(dir,
                "metrics.arg.influxdbUrl=http://localhost:8086\n"
                        + "metrics.arg.application=checkout\n"
                        + "metrics.enabled=true\n"
                        + "host=example.com\n").withPrefix("metrics.arg.");

        assertEquals(2, args.size(), "only the prefixed keys, and the prefix is stripped");
        assertEquals("http://localhost:8086", args.get("influxdbUrl"));
        assertEquals("checkout", args.get("application"));
    }

    @Test
    void asPropertiesReturnsADefensiveCopy(@TempDir Path dir) throws IOException {
        EnvironmentConfig cfg = config(dir, "host=example.com\nport=8080\n");

        java.util.Properties copy = cfg.asProperties();
        copy.setProperty("host", "mutated");

        assertEquals("example.com", cfg.host(), "mutating the copy must not affect the config");
    }
}
