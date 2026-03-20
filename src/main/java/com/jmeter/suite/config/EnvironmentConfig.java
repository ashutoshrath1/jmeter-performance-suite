package com.jmeter.suite.config;

import org.apache.jmeter.util.JMeterUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

public final class EnvironmentConfig {

    private static final String ENV_CONFIG_DIR = "config/environments";

    private final String name;
    private final Properties properties;

    private EnvironmentConfig(String name, Properties properties) {
        this.name = Objects.requireNonNull(name, "name");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

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

    public String name() {
        return name;
    }

    public String host() {
        return get("host", "jsonplaceholder.typicode.com");
    }

    public String protocol() {
        return get("protocol", "https");
    }

    public String healthPath() {
        return get("health.path", "/posts");
    }

    public int healthTimeoutMs() {
        return Integer.parseInt(get("health.timeout.ms", "5000"));
    }

    public boolean healthCheckEnabled() {
        return Boolean.parseBoolean(get("health.check.enabled", "true"));
    }

    public boolean autoOpenReports() {
        return Boolean.parseBoolean(get("auto_open_reports", "true"));
    }

    public double maxErrorRatePercent() {
        return Double.parseDouble(get("max_error_rate_percent", "0"));
    }

    public void pushToJMeter() {
        properties.forEach((key, value) -> JMeterUtils.setProperty(String.valueOf(key), String.valueOf(value)));
        JMeterUtils.setProperty("environment", name);
    }

    private String get(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value != null && !value.trim().isEmpty() ? value : defaultValue;
    }
}
