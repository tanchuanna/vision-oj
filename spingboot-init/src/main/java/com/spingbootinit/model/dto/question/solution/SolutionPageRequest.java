package com.spingbootinit.model.dto.question.solution;

import com.spingbootinit.model.dto.question.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SolutionPageRequest extends PageRequest {
    private String questionId;
    private String keyword;
    private Integer reviewStatus;
}
