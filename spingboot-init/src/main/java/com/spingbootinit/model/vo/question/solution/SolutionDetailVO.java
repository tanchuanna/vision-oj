package com.spingbootinit.model.vo.question.solution;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class SolutionDetailVO extends SolutionListItemVO {
    private String accessLevel;
    private String content;
    private String reviewReason;
    private Boolean liked;
    private Boolean mine;
    private Date updateTime;
}
