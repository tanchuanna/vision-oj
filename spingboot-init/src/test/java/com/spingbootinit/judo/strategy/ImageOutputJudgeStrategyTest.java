package com.spingbootinit.judo.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageOutputJudgeStrategyTest {
    @Test
    void semanticConfigurationOverridesLegacyOverloadedTolerance() {
        com.spingbootinit.model.dto.question.JudgeConfig config = new com.spingbootinit.model.dto.question.JudgeConfig();
        config.setOcrEditDistance(2);
        assertTrue(ImageOutputJudgeStrategy.outputsMatch("IMAGE_OCR", "abcdef", "abxxef", 0, config));

        String expected = "[{\"label\":\"car\",\"x1\":0.1,\"y1\":0.1,\"x2\":0.6,\"y2\":0.6}]";
        String shifted = "[{\"label\":\"car\",\"x1\":0.12,\"y1\":0.12,\"x2\":0.62,\"y2\":0.62}]";
        config.setIouThreshold(0.86);
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_OBJECT_DETECTION", expected, shifted, 50, config));
        config.setIouThreshold(0.85);
        assertTrue(ImageOutputJudgeStrategy.outputsMatch("IMAGE_OBJECT_DETECTION", expected, shifted, 90, config));
    }

    @Test
    void analysisUsesIndependentTolerancesAndIncludesBoundary() {
        com.spingbootinit.model.dto.question.JudgeConfig config = new com.spingbootinit.model.dto.question.JudgeConfig();
        config.setAnalysisVersion(2);
        config.setMeanGrayTolerance(0.25);
        config.setEdgeStrengthTolerance(2.0);
        String expected = "{\"width\":100,\"height\":80,\"mode\":\"RGB\",\"mean_gray\":120.5,\"edge_strength\":10.0}";

        assertTrue(ImageOutputJudgeStrategy.outputsMatch("IMAGE_ANALYSIS", expected,
                "{\"width\":100,\"height\":80,\"mode\":\"RGB\",\"mean_gray\":120.75,\"edge_strength\":12.0}", 0, config));
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_ANALYSIS", expected,
                "{\"width\":100,\"height\":80,\"mode\":\"RGB\",\"mean_gray\":120.76,\"edge_strength\":12.0}", 99, config));
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_ANALYSIS", expected,
                "{\"width\":100,\"height\":80,\"mode\":\"RGB\",\"mean_gray\":120.75,\"edge_strength\":12.01}", 99, config));
    }

    @Test
    void policyKeepsHistoricalToleranceMeaningWhenSemanticFieldsAreAbsent() {
        com.spingbootinit.model.dto.question.JudgeConfig config = new com.spingbootinit.model.dto.question.JudgeConfig();
        assertEquals(3, ImageJudgeConfigPolicy.countTolerance(config, 3));
        assertEquals(0.55, ImageJudgeConfigPolicy.iouThreshold(config, 55));
        assertEquals(2, ImageJudgeConfigPolicy.ocrEditDistance(config, 2));
        assertEquals(4.0, ImageJudgeConfigPolicy.meanGrayTolerance(config, 4));
        assertEquals(4.0, ImageJudgeConfigPolicy.edgeStrengthTolerance(config, 4));
    }

    @Test
    void newAnalysisRuleRequiresExactDimensionsAndCompleteFields() {
        com.spingbootinit.model.dto.question.JudgeConfig config = new com.spingbootinit.model.dto.question.JudgeConfig();
        config.setAnalysisVersion(2);
        String expected = "{\"width\":100,\"height\":80,\"mode\":\"RGB\",\"mean_gray\":120.5,\"edge_strength\":10.0}";
        assertTrue(ImageOutputJudgeStrategy.outputsMatch("IMAGE_ANALYSIS", expected,
                "{\"height\":80,\"width\":100,\"mode\":\"RGB\",\"mean_gray\":120.9,\"edge_strength\":10.8}", 2, config));
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_ANALYSIS", expected, expected.replace("\"width\":100", "\"width\":101"), 2, config));
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_ANALYSIS", expected, "{\"width\":100}", 2, config));
    }
    @Test
    void classificationIgnoresCaseAndQuotes() {
        assertTrue(ImageOutputJudgeStrategy.outputsMatch("IMAGE_CLASSIFICATION", "car", "\"CAR\"", 0));
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_CLASSIFICATION", "car", "bus", 0));
    }

    @Test
    void ocrUsesNormalizedEditDistance() {
        assertTrue(ImageOutputJudgeStrategy.outputsMatch("IMAGE_OCR", "Hello，世界", "hello 世界!", 0));
        assertTrue(ImageOutputJudgeStrategy.outputsMatch("IMAGE_OCR", "abcdef", "abcxef", 1));
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_OCR", "abcdef", "abxxxx", 1));
    }

    @Test
    void detectionMatchesByLabelAndIou() {
        String expected = "[{\"label\":\"car\",\"x1\":0.1,\"y1\":0.1,\"x2\":0.6,\"y2\":0.6}]";
        String close = "[{\"label\":\"car\",\"x1\":0.12,\"y1\":0.12,\"x2\":0.62,\"y2\":0.62}]";
        assertTrue(ImageOutputJudgeStrategy.outputsMatch("IMAGE_OBJECT_DETECTION", expected, close, 50));
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_OBJECT_DETECTION", expected, close.replace("car", "bus"), 50));
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_OBJECT_DETECTION", expected,
                "[{\"label\":\"car\",\"x1\":-0.1,\"y1\":0.1,\"x2\":0.6,\"y2\":0.6}]", 50));
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_OBJECT_DETECTION", expected,
                "[{\"label\":\"\",\"x1\":0.1,\"y1\":0.1,\"x2\":0.6,\"y2\":0.6}]", 50));
    }

    @Test
    void analysisSupportsNumericTolerance() {
        assertTrue(ImageOutputJudgeStrategy.outputsMatch("IMAGE_ANALYSIS", "{\"width\":100,\"mean_gray\":120.5}", "{\"mean_gray\":121.2,\"width\":100}", 1));
        assertFalse(ImageOutputJudgeStrategy.outputsMatch("IMAGE_ANALYSIS", "{\"width\":100}", "{\"width\":103}", 1));
    }
}
