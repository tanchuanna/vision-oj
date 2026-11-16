package com.spingbootinit.model.dto.question;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    /** 新图像属性题使用字段级比较；缺省时兼容旧题的统一整数容差。 */
    private Integer analysisVersion;
}
