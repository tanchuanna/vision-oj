package com.spingbootinit.model.dto.question.solution;

import lombok.Data;

@Data
public class SolutionReviewRequest {
    private String id;
    private Boolean approved;
    private String reason;
}
