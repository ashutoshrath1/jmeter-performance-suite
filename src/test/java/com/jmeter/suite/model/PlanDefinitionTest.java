package com.jmeter.suite.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies suite resolution behavior for known and unknown suite ids.
 */
class PlanDefinitionTest {

    /**
     * Confirms quick suite resolves to the baseline plan only.
     */
    @Test
    void quickSuiteContainsOnlyBaseline() {
        List<PlanDefinition> plans = PlanDefinition.resolveSuite("quick");

        assertEquals(List.of(PlanDefinition.BASELINE), plans);
    }

    /**
     * Confirms load suite resolves to baseline and stress plans.
     */
    @Test
    void loadSuiteContainsBaselineAndStress() {
        List<PlanDefinition> plans = PlanDefinition.resolveSuite("load");

        assertEquals(List.of(PlanDefinition.BASELINE, PlanDefinition.STRESS), plans);
    }

    /**
     * Confirms unknown suite names resolve to an empty plan list.
     */
    @Test
    void unknownSuiteReturnsEmptyList() {
        assertTrue(PlanDefinition.resolveSuite("unknown").isEmpty());
    }
}
