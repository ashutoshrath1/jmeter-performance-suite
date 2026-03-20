package com.jmeter.suite.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunnerArgsTest {

    @Test
    void defaultsAreAppliedWhenNoArgsProvided() {
        RunnerArgs args = RunnerArgs.from(new String[0]);

        assertEquals("dev", args.environment());
        assertEquals("quick", args.suite());
    }

    @Test
    void providedArgsAreTrimmedAndUsed() {
        RunnerArgs args = RunnerArgs.from(new String[]{" staging ", " stress "});

        assertEquals("staging", args.environment());
        assertEquals("stress", args.suite());
    }
}
