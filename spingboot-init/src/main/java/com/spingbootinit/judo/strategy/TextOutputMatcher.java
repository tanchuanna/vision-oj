package com.spingbootinit.judo.strategy;

import java.util.Objects;

/** Output comparison shared by public runs and formal judging. */
public final class TextOutputMatcher {
    private TextOutputMatcher() { }

    public static boolean matches(String expected, String actual, Double tolerance) {
        if (tolerance == null) {
            return Objects.equals(expected, actual);
        }
        if (!Double.isFinite(tolerance) || tolerance < 0) {
            return false;
        }
        String left = expected == null ? "" : expected.trim();
        String right = actual == null ? "" : actual.trim();
        if (left.isEmpty() || right.isEmpty()) {
            return left.equals(right);
        }
        String[] expectedTokens = left.split("\\s+");
        String[] actualTokens = right.split("\\s+");
        if (expectedTokens.length != actualTokens.length) {
            return false;
        }
        for (int i = 0; i < expectedTokens.length; i++) {
            if (expectedTokens[i].equals(actualTokens[i])) {
                continue;
            }
            try {
                double a = Double.parseDouble(expectedTokens[i]);
                double b = Double.parseDouble(actualTokens[i]);
                if (!Double.isFinite(a) || !Double.isFinite(b)
                        || Math.abs(a - b) > tolerance * Math.max(1.0, Math.abs(a))) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }
}
