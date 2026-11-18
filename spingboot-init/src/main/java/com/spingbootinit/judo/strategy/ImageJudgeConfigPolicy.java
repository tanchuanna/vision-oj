package com.spingbootinit.judo.strategy;

import com.spingbootinit.model.dto.question.JudgeConfig;

/**
 * 五类图像题的判题配置解析规则。
 *
 * <p>新题优先使用 judgeConfig 中语义明确的字段；历史题缺少这些字段时，
 * 继续按原有 countTolerance 的题型含义读取，避免历史数据失效。</p>
 */
public final class ImageJudgeConfigPolicy {
    public static final double DEFAULT_IOU_THRESHOLD = 0.50;
    public static final double DEFAULT_MEAN_GRAY_TOLERANCE = 0.50;
    public static final double DEFAULT_EDGE_STRENGTH_TOLERANCE = 1.00;

    private ImageJudgeConfigPolicy() {
    }

    public static int countTolerance(JudgeConfig config, Integer legacyTolerance) {
        if (config != null && config.getCountTolerance() != null) {
            return Math.max(0, config.getCountTolerance());
        }
        return Math.max(0, legacyTolerance == null ? 0 : legacyTolerance);
    }

    public static double iouThreshold(JudgeConfig config, Integer legacyTolerance) {
        if (config != null && finiteNonNegative(config.getIouThreshold())) {
            return clamp(config.getIouThreshold(), 0.10, 0.95);
        }
        int legacy = legacyTolerance == null ? 0 : legacyTolerance;
        return legacy <= 0 ? DEFAULT_IOU_THRESHOLD : clamp(legacy / 100.0, 0.10, 0.95);
    }

    public static int ocrEditDistance(JudgeConfig config, Integer legacyTolerance) {
        if (config != null && config.getOcrEditDistance() != null) {
            return Math.max(0, config.getOcrEditDistance());
        }
        return Math.max(0, legacyTolerance == null ? 0 : legacyTolerance);
    }

    public static double meanGrayTolerance(JudgeConfig config, Integer legacyTolerance) {
        if (config != null && finiteNonNegative(config.getMeanGrayTolerance())) {
            return config.getMeanGrayTolerance();
        }
        if (config != null && Integer.valueOf(2).equals(config.getAnalysisVersion())) {
            return DEFAULT_MEAN_GRAY_TOLERANCE;
        }
        return Math.max(0, legacyTolerance == null ? 0 : legacyTolerance);
    }

    public static double edgeStrengthTolerance(JudgeConfig config, Integer legacyTolerance) {
        if (config != null && finiteNonNegative(config.getEdgeStrengthTolerance())) {
            return config.getEdgeStrengthTolerance();
        }
        if (config != null && Integer.valueOf(2).equals(config.getAnalysisVersion())) {
            return DEFAULT_EDGE_STRENGTH_TOLERANCE;
        }
        return Math.max(0, legacyTolerance == null ? 0 : legacyTolerance);
    }

    public static boolean usesStructuredAnalysis(JudgeConfig config) {
        return config != null && (Integer.valueOf(2).equals(config.getAnalysisVersion())
                || config.getMeanGrayTolerance() != null
                || config.getEdgeStrengthTolerance() != null);
    }

    private static boolean finiteNonNegative(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
