package com.jmeter.suite.config;

public final class RunnerArgs {

    private final String environment;
    private final String suite;

    private RunnerArgs(String environment, String suite) {
        this.environment = environment;
        this.suite = suite;
    }

    public static RunnerArgs from(String[] args) {
        String environment = args.length > 0 ? args[0] : "dev";
        String suite = args.length > 1 ? args[1] : "quick";
        return new RunnerArgs(environment.trim(), suite.trim());
    }

    public String environment() {
        return environment;
    }

    public String suite() {
        return suite;
    }
}
