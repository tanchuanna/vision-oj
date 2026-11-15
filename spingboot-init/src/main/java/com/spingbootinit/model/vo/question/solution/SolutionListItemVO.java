package com.spingbootinit.model.vo.question.solution;

import lombok.Data;

import java.util.Date;

@Data
public class SolutionListItemVO {
    private String id;
    private String questionId;
    private String userId;
    private String authorName;
    private String authorAvatar;
    private String title;
    private String summary;
    private String hintContent;
    private Integer reviewStatus;
    private Boolean official;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Date publishedAt;
    private Date createTime;
}
