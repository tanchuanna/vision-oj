package com.spingbootinit.model.dto.bailian;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CodeEditRequest {
    @Size(max = 32) private String questionId;
    @NotBlank @Size(max = 1000) private String instruction;
    @Size(max = 20000) private String code;
    @NotBlank @Size(max = 16) private String language;
    @Size(max = 500) private String questionTitle;
    @Size(max = 12000) private String questionContent;
    @Size(max = 6000) private String sampleCases;
    @Size(max = 10000) private String runResult;
    @Size(max = 12000) private String referenceAnswer;
    @Size(max = 20) private String providerCode;
    @Size(max = 100) private String modelId;
}
