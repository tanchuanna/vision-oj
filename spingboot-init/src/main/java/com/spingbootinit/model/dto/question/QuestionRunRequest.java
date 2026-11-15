package com.spingbootinit.model.dto.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 运行代码（样例用例，不入库、不判正式榜）
 * <p>题目 id 使用字符串，避免 JSON 数字超过 JS 安全整数时前端失真；后端再解析为 Long。</p>
 */
@Data
public class QuestionRunRequest implements Serializable {

    @NotBlank(message = "题目 id 不能为空")
    @Pattern(regexp = "\\d{1,24}", message = "题目 id 须为数字")
    private String questionId;

    @NotBlank(message = "语言不能为空")
    private String language;

    @NotBlank(message = "代码不能为空")
    @Size(max = 200_000, message = "代码过长")
    private String code;

    /** SAMPLE（默认）运行公开样例；CUSTOM 仅运行一组用户输入。 */
    @Pattern(regexp = "(?i)SAMPLE|CUSTOM", message = "运行模式仅支持 SAMPLE 或 CUSTOM")
    private String mode;

    /** 自定义标准输入允许为空，并严格保留空格与换行。 */
    @Size(max = 50_000, message = "自定义输入不能超过50000个字符")
    private String customInput;

    private static final long serialVersionUID = 1L;
}
