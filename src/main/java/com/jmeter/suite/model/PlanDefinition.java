package com.jmeter.suite.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A single JMeter plan: its stable identifier and the JMX file backing it.
 */
public final class PlanDefinition {

    private final String id;
    private final Path jmxPath;

    /**
     * Creates a plan definition with identifier and JMX path.
     */
    public PlanDefinition(String id, Path jmxPath) {
        this.id = Objects.requireNonNull(id, "id");
        this.jmxPath = Objects.requireNonNull(jmxPath, "jmxPath");
    }

    /**
     * Returns the stable plan identifier, derived from the JMX filename.
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
     * Returns the identifier, so log lines and collections read naturally.
     */
    @Override
    public String toString() {
        return id;
    }

    /**
     * Compares definitions by identifier and path.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlanDefinition)) {
            return false;
        }
        PlanDefinition that = (PlanDefinition) other;
        return id.equals(that.id) && jmxPath.equals(that.jmxPath);
    }

    /**
     * Returns a hash consistent with {@link #equals(Object)}.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, jmxPath);
    }
}
