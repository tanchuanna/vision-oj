package com.spingbootinit.model.dto.question;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 题目配置
 */
@Data
public class JudgeConfig {

    /**
     * 时间限制（ms）
     */
    @NotNull(message = "时间限制不能为空")
    @Positive(message = "时间限制必须大于 0")
    private Long timeLimit;

    /**
     * 内存限制（KB）
     */
    @NotNull(message = "内存限制不能为空")
    @Positive(message = "内存限制必须大于 0")
    private Long memoryLimit;

    /**
     * 堆栈限制（KB）
     */
    @NotNull(message = "堆栈限制不能为空")
    @Positive(message = "堆栈限制必须大于 0")
    private Long stackLimit;

    /** Optional relative/absolute token tolerance for imported floating-point outputs. */
    @DecimalMin(value = "0.0", message = "浮点容差不能小于 0")
    @DecimalMax(value = "0.01", message = "浮点容差不能大于 0.01")
    private Double floatTolerance;

    /** 新图像属性题使用字段级比较；缺省时兼容旧题的统一整数容差。 */
    private Integer analysisVersion;

    /** 图像目标计数题：每个类别允许的数量误差。 */
    @Min(value = 0, message = "数量允许误差不能小于 0")
    @Max(value = 100, message = "数量允许误差不能大于 100")
    private Integer countTolerance;

    /** 目标位置检测题：归一化 IoU 阈值。 */
    @DecimalMin(value = "0.1", message = "IoU 阈值不能小于 0.1")
    @DecimalMax(value = "0.95", message = "IoU 阈值不能大于 0.95")
    private Double iouThreshold;

    /** OCR 题：规范化文本后允许的编辑距离。 */
    @Min(value = 0, message = "OCR 允许错误字符数不能小于 0")
    @Max(value = 100, message = "OCR 允许错误字符数不能大于 100")
    private Integer ocrEditDistance;

    /** 图像数值分析题：平均灰度允许误差。 */
    @DecimalMin(value = "0.0", message = "平均灰度允许误差不能小于 0")
    @DecimalMax(value = "255.0", message = "平均灰度允许误差不能大于 255")
    private Double meanGrayTolerance;

    /** 图像数值分析题：边缘强度允许误差。 */
    @DecimalMin(value = "0.0", message = "边缘强度允许误差不能小于 0")
    @DecimalMax(value = "255.0", message = "边缘强度允许误差不能大于 255")
    private Double edgeStrengthTolerance;
}
