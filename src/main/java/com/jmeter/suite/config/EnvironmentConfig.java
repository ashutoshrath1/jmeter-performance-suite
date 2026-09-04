package com.jmeter.suite.config;

import org.apache.jmeter.util.JMeterUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads and exposes environment-specific settings for a test run.
 */
public final class EnvironmentConfig {

    private static final String ENV_CONFIG_DIR = "config/environments";

    private final String name;
    private final Properties properties;

    /**
     * Creates an immutable environment configuration view.
     */
    private EnvironmentConfig(String name, Properties properties) {
        this.name = Objects.requireNonNull(name, "name");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Loads an environment configuration file by environment name.
     */
    public static EnvironmentConfig load(String environment) throws IOException {
        Path envPath = Paths.get(ENV_CONFIG_DIR, environment + ".properties");
        if (!Files.exists(envPath)) {
            throw new IllegalStateException("Environment config not found: " + envPath);
        }

        Properties properties = new Properties();
        try (InputStream is = Files.newInputStream(envPath)) {
            properties.load(is);
        }

        return new EnvironmentConfig(environment, properties);
    }

    /**
     * Returns the environment label used for logging and properties.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the target host for HTTP samplers.
     */
    public String host() {
        return get("host", "jsonplaceholder.typicode.com");
    }

    /**
     * Returns the protocol for HTTP samplers.
     */
    public String protocol() {
        return get("protocol", "https");
    }

    /**
     * Returns the target port, or an empty string when the protocol default should apply.
     */
    public String port() {
        return get("port", "");
    }

    /**
     * Returns the target origin as {@code protocol://host[:port]}, omitting the port when it is
     * unset or already the default for the protocol.
     */
    public String baseUrl() {
        String protocol = protocol();
        String port = port().trim();
        boolean defaultPort = port.isEmpty()
                || ("https".equalsIgnoreCase(protocol) && "443".equals(port))
                || ("http".equalsIgnoreCase(protocol) && "80".equals(port));
        return protocol + "://" + host() + (defaultPort ? "" : ":" + port);
    }

    /**
     * Returns the health-check endpoint path.
     */
    public String healthPath() {
        return get("health.path", "/posts");
    }

    /**
     * Returns the health-check timeout in milliseconds.
     */
    public int healthTimeoutMs() {
        return Integer.parseInt(get("health.timeout.ms", "5000"));
    }

    /**
     * Returns whether health checks are enabled for this environment.
     */
    public boolean healthCheckEnabled() {
        return Boolean.parseBoolean(get("health.check.enabled", "true"));
    }

    /**
     * Returns whether HTML reports should auto-open after a run.
     */
    public boolean autoOpenReports() {
        return Boolean.parseBoolean(get("auto_open_reports", "true"));
    }

    /**
     * Returns the maximum allowed error rate percentage.
     */
    public double maxErrorRatePercent() {
        return Double.parseDouble(get("max_error_rate_percent", "0"));
    }

    /**
     * Returns the 95th-percentile response time budget in milliseconds, or 0 when ungated.
     *
     * <p>Accepts the legacy {@code max_response_time_ms} key as an alias so existing environment
     * files keep working; the gate is percentile-based because an absolute maximum fails on a single
     * GC pause or warm-up outlier.
     */
    public long p95ResponseTimeMs() {
        String value = properties.getProperty("p95_response_time_ms");
        if (value == null || value.trim().isEmpty()) {
            value = get("max_response_time_ms", "0");
        }
        return Long.parseLong(value.trim());
    }

    /**
     * Returns the minimum acceptable throughput in samples per second, or 0 when ungated.
     */
    public double minThroughputTps() {
        return Double.parseDouble(get("min_throughput_tps", "0"));
    }

    /**
     * Pushes all loaded environment properties into JMeter runtime properties.
     */
    public void pushToJMeter() {
        properties.forEach((key, value) -> JMeterUtils.setProperty(String.valueOf(key), String.valueOf(value)));
        JMeterUtils.setProperty("environment", name);
    }

    /**
     * Returns a property value with fallback default handling.
     */
    private String get(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value != null && !value.trim().isEmpty() ? value : defaultValue;
    }
}
