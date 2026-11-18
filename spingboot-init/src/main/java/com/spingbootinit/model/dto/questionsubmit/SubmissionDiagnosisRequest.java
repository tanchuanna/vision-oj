package com.spingbootinit.model.dto.questionsubmit;

import lombok.Data;

/**
 * 提交结果 AI 诊断请求：providerCode / modelId 为空时使用站点默认模型。
 */
@Data
public class SubmissionDiagnosisRequest {
    private String providerCode;
    private String modelId;
}
