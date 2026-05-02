package com.jmeter.suite.config;

/**
 * Stores normalized runner arguments for environment and suite selection.
 */
public final class RunnerArgs {

    private final String environment;
    private final String suite;

    /**
     * Creates an immutable argument container.
     */
    private RunnerArgs(String environment, String suite) {
        this.environment = environment;
        this.suite = suite;
    }

    /**
     * Builds runner arguments from CLI inputs with defaults.
     */
    public static RunnerArgs from(String[] args) {
        String environment = args.length > 0 ? args[0] : "dev";
        String suite = args.length > 1 ? args[1] : "quick";
        return new RunnerArgs(environment.trim(), suite.trim());
    }

    /**
     * Returns the target environment name.
     */
    public String environment() {
        return environment;
    }

    /**
     * Returns the requested suite identifier.
     */
    public String suite() {
        return suite;
    }
}
