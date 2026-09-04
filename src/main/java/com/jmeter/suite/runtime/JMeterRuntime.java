package com.jmeter.suite.runtime;

import com.jmeter.suite.util.Log;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.util.JMeterUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Bootstraps the embedded JMeter runtime.
 *
 * <p>JMeter expects to be started from a full distribution. Running it embedded means wiring up by
 * hand the pieces its launcher would normally provide: the home directory, logging, the save-service
 * aliases, and the function registry. Each omission fails quietly rather than loudly, so this is
 * collected in one place.
 */
public final class JMeterRuntime {

    private static final String JMETER_PROPERTIES = "config/jmeter.properties";
    private static final String LOGS_DIR = "logs";

    /**
     * Prevents instantiation of this utility type.
     */
    private JMeterRuntime() {
    }

    /**
     * Initialises JMeter properties, logging, the function registry, and the save service.
     */
    public static void initialize() throws IOException {
        configureLogging();

        Path jmeterProps = Paths.get(JMETER_PROPERTIES);
        if (!Files.exists(jmeterProps)) {
            throw new IllegalStateException("Missing JMeter properties: " + JMETER_PROPERTIES);
        }

        JMeterUtils.setJMeterHome(Paths.get(".").toAbsolutePath().normalize().toString());
        JMeterUtils.loadJMeterProperties(jmeterProps.toString());
        JMeterUtils.setProperty("log_file", Paths.get(LOGS_DIR, "jmeter.log").toString());

        registerFunctionSearchPath();
        ensureReportDefaults();
        JMeterUtils.initLocale();

        // Registers the XStream aliases that let a .jmx deserialize. Without bin/saveservice.properties
        // this throws CannotResolveClassException on the test plan's root element.
        SaveService.loadProperties();
    }

    /**
     * Routes JMeter's log4j output to the suite's logs directory.
     */
    private static void configureLogging() {
        String log4jConfig = Paths.get("bin", "log4j2.xml").toAbsolutePath().normalize().toString();
        String jmeterLogFile = Paths.get(LOGS_DIR, "jmeter.log").toString();
        System.setProperty("log4j2.configurationFile", log4jConfig);
        System.setProperty("log4j.configurationFile", log4jConfig);
        System.setProperty("log4j2.statusLevel", "error");
        System.setProperty("log4j2.disable.jmx", "true");
        System.setProperty("log_file", jmeterLogFile);
        System.setProperty("jmeter.logfile", jmeterLogFile);
    }

    /**
     * Points JMeter's function scanner at this executable so {@code ${__...}} calls resolve.
     *
     * <p>JMeter discovers {@code Function} implementations by scanning {@code search_paths} plus
     * {@code $JMETER_HOME/lib/ext}. An embedded runner has no {@code lib/ext}, so without this the
     * registry comes up empty and every function - {@code __P}, {@code __property}, {@code __time} -
     * is silently left in the test plan as literal text.
     */
    private static void registerFunctionSearchPath() {
        try {
            Path self = Paths.get(JMeterRuntime.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            String existing = JMeterUtils.getPropDefault("search_paths", "");
            String updated = existing.isEmpty()
                    ? self.toAbsolutePath().toString()
                    : existing + ";" + self.toAbsolutePath();
            JMeterUtils.setProperty("search_paths", updated);
        } catch (Exception ex) {
            Log.info("Warning: could not register function search path: " + ex.getMessage());
        }
    }

    /**
     * Applies Apdex thresholds when the active properties leave them unset or unresolved.
     */
    private static void ensureReportDefaults() {
        setIfMissing("jmeter.reportgenerator.apdex_satisfied_threshold", "500");
        setIfMissing("jmeter.reportgenerator.apdex_tolerated_threshold", "1500");
    }

    /**
     * Sets a JMeter property only when the current value is absent or still contains a placeholder.
     */
    private static void setIfMissing(String key, String defaultValue) {
        String current = JMeterUtils.getProperty(key);
        if (current == null || current.trim().isEmpty() || current.contains("${")) {
            JMeterUtils.setProperty(key, defaultValue);
        }
    }
}
