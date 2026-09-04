package com.jmeter.suite.metrics;

import com.jmeter.suite.config.EnvironmentConfig;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.visualizers.backend.BackendListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a configured {@link BackendListener} so runs stream metrics to a time-series backend.
 *
 * <p>Without this every run is an island: results land in a timestamped file and nothing compares
 * them, so there is no way to see that p95 has drifted since last release. Streaming samples to
 * InfluxDB, Graphite or Prometheus is what turns a pile of runs into a trend.
 *
 * <p>The factory stays backend-agnostic. It knows how to assemble a listener and pass arguments
 * through; which client to load and what to pass it are configuration:
 *
 * <pre>
 * metrics.enabled=true
 * metrics.classname=org.apache.jmeter.visualizers.backend.influxdb.InfluxdbBackendListenerClient
 * metrics.arg.influxdbUrl=http://localhost:8086/write?db=jmeter
 * metrics.arg.application=checkout-api
 * </pre>
 */
public final class BackendListenerFactory {

    /** Enables metric streaming for the environment. */
    public static final String ENABLED_KEY = "metrics.enabled";
    /** Fully-qualified BackendListenerClient implementation to load. */
    public static final String CLASSNAME_KEY = "metrics.classname";
    /** Bounded queue depth; samples are dropped rather than blocking the load when it fills. */
    public static final String QUEUE_SIZE_KEY = "metrics.queue.size";
    /** Prefix for arguments handed to the backend client verbatim. */
    public static final String ARG_PREFIX = "metrics.arg.";

    private static final String DEFAULT_CLASSNAME =
            "org.apache.jmeter.visualizers.backend.influxdb.InfluxdbBackendListenerClient";
    private static final String DEFAULT_QUEUE_SIZE = "5000";

    /**
     * Prevents instantiation of this factory type.
     */
    private BackendListenerFactory() {
    }

    /**
     * Creates a listener for the environment, or empty when metric streaming is disabled.
     *
     * <p>The plan and environment are added as arguments when the configuration does not set them,
     * so samples from different plans and environments stay distinguishable in one backend.
     */
    public static Optional<BackendListener> create(EnvironmentConfig config, String planId) {
        if (!config.flag(ENABLED_KEY, false)) {
            return Optional.empty();
        }

        BackendListener listener = new BackendListener();
        listener.setName("Metrics Backend");
        listener.setClassname(config.value(CLASSNAME_KEY, DEFAULT_CLASSNAME));
        listener.setQueueSize(config.value(QUEUE_SIZE_KEY, DEFAULT_QUEUE_SIZE));
        listener.setProperty(org.apache.jmeter.testelement.TestElement.TEST_CLASS,
                BackendListener.class.getName());
        listener.setProperty(org.apache.jmeter.testelement.TestElement.GUI_CLASS,
                "org.apache.jmeter.visualizers.backend.BackendListenerGui");
        listener.setArguments(buildArguments(config, planId));
        return Optional.of(listener);
    }

    /**
     * Assembles the client arguments, defaulting the identifying tags when unset.
     */
    private static Arguments buildArguments(EnvironmentConfig config, String planId) {
        Map<String, String> values = new LinkedHashMap<>(config.withPrefix(ARG_PREFIX));
        values.putIfAbsent("application", "jmeter-performance-suite");
        values.putIfAbsent("measurement", "jmeter");
        values.putIfAbsent("testTitle", planId);
        values.putIfAbsent("eventTags", "plan=" + planId + ",environment=" + config.name());

        Arguments arguments = new Arguments();
        values.forEach(arguments::addArgument);
        return arguments;
    }
}
