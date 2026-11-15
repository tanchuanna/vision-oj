package com.spingbootinit.model.vo.questionsubmitvo;

import lombok.Data;

import java.util.Date;

@Data
public class SubmissionListItemVO {
    private String submissionId;
    private Long globalSubmitNo;
    private String questionId;
    private String questionTitle;
    private String userId;
    private String username;
    private String nickname;
    private String avatar;
    private String language;
    private Integer status;
    private String verdict;
    private Long runtimeMs;
    private Long memoryKb;
    private Integer codeLength;
    private String contestId;
    private Date createTime;
}
