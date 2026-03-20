package com.jmeter.suite.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanDefinitionTest {

    @Test
    void quickSuiteContainsOnlyBaseline() {
        List<PlanDefinition> plans = PlanDefinition.resolveSuite("quick");

        assertEquals(List.of(PlanDefinition.BASELINE), plans);
    }

    @Test
    void loadSuiteContainsBaselineAndStress() {
        List<PlanDefinition> plans = PlanDefinition.resolveSuite("load");

        assertEquals(List.of(PlanDefinition.BASELINE, PlanDefinition.STRESS), plans);
    }

    @Test
    void unknownSuiteReturnsEmptyList() {
        assertTrue(PlanDefinition.resolveSuite("unknown").isEmpty());
    }
}
