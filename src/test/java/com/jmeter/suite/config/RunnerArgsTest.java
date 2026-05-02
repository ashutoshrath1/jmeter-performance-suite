package com.jmeter.suite.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies argument parsing defaults and normalization.
 */
class RunnerArgsTest {

    /**
     * Confirms defaults are used when no CLI arguments are provided.
     */
    @Test
    void defaultsAreAppliedWhenNoArgsProvided() {
        RunnerArgs args = RunnerArgs.from(new String[0]);

        assertEquals("dev", args.environment());
        assertEquals("quick", args.suite());
    }

    /**
     * Confirms provided CLI arguments are trimmed and applied.
     */
    @Test
    void providedArgsAreTrimmedAndUsed() {
        RunnerArgs args = RunnerArgs.from(new String[]{" staging ", " stress "});

        assertEquals("staging", args.environment());
        assertEquals("stress", args.suite());
    }
}
