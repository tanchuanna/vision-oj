package com.spingbootinit.model.dto.teaching;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 老师一键布置练习的请求。
 */
@Data
public class PracticeAssignRequest {
    /** 目标学生，支持一次给多名学生各生成一份练习 */
    private List<Long> studentIds;
    /** 每人题目数量，默认 5，限制 1-10 */
    private Integer questionCount;
    /** 练习标题，为空时自动生成 */
    private String title;
    private String remark;
    private Date dueTime;
}
