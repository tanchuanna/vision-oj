package com.spingbootinit.model.vo.questionsubmitvo;

import lombok.Data;

import java.util.Date;

/**
 * 题目排行榜公开行。不包含提交主键、代码、邮箱等敏感信息。
 */
@Data
public class QuestionRankingItemVO {
    private Integer rankNo;
    private String userName;
    private String userAvatar;
    private Long runtimeMs;
    private Long memoryKb;
    private String language;
    private Integer codeLength;
    private Date createTime;
}
