package com.spingbootinit.judo.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextOutputMatcherTest {
    @Test
    void exactModePreservesExistingComparison() {
        assertTrue(TextOutputMatcher.matches("Case #1: 2", "Case #1: 2", null));
        assertFalse(TextOutputMatcher.matches("Case #1: 2", "Case #1: 2\n", null));
    }

    @Test
    void floatingPointModeComparesNumericTokensAndKeepsLabelsExact() {
        assertTrue(TextOutputMatcher.matches("Case #1: 1.000000", "Case #1: 1.0000005", 1e-6));
        assertTrue(TextOutputMatcher.matches("10.0\n20.0", "10.000005 20.00001", 1e-6));
        assertFalse(TextOutputMatcher.matches("Case #1: 1.0", "Case #2: 1.0", 1e-6));
        assertFalse(TextOutputMatcher.matches("1.0", "1.000002", 1e-6));
        assertFalse(TextOutputMatcher.matches("1.0", "NaN", 1e-6));
        assertFalse(TextOutputMatcher.matches("1.0 2.0", "1.0", 1e-6));
    }
}
