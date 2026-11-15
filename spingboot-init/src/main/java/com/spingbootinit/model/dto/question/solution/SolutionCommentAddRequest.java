package com.spingbootinit.model.dto.question.solution;

import lombok.Data;

@Data
public class SolutionCommentAddRequest {
    private String solutionId;
    private String parentId;
    private String content;
}
