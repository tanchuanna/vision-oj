package com.spingbootinit.model.dto.question.solution;

import lombok.Data;

@Data
public class SolutionSaveRequest {
    private String id;
    private String questionId;
    private String title;
    private String summary;
    private String hintContent;
    private String content;
}
