package com.spingbootinit.model.vo.teaching;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 一份练习及其完成进度。
 */
@Data
public class PracticeAssignmentVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teacherId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long studentId;
    private String studentName;
    private String teacherName;
    private String title;
    private String remark;
    private Date dueTime;
    private Date createTime;
    private Integer itemCount;
    private Integer solvedCount;
    private Integer progress;
    private Integer status;
    private List<PracticeItemVO> items = new ArrayList<>();
}
