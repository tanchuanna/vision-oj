package com.spingbootinit.model.vo.questionsubmitvo;

import lombok.Data;

import java.util.Date;

@Data
public class SubmissionDetailVO {
    private String submissionId;
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
    private String code;
    private String errorDetail;
    private String contestId;
    private Date createTime;
    private boolean ownSubmission;
}
