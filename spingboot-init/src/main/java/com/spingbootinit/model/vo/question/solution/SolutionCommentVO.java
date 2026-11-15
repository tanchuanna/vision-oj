package com.spingbootinit.model.vo.question.solution;

import lombok.Data;

import java.util.Date;

@Data
public class SolutionCommentVO {
    private String id;
    private String solutionId;
    private String userId;
    private String parentId;
    private String authorName;
    private String authorAvatar;
    private String content;
    private Date createTime;
    private Boolean mine;
}
