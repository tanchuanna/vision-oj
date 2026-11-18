package com.spingbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 老师布置的个性化练习（一次布置对应一名学生）。
 */
@TableName("practice_assignment")
@Data
@EqualsAndHashCode(callSuper = true)
public class PracticeAssignment extends BaseEntity implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private Long teacherId;

    private Long studentId;

    private String title;

    private String remark;

    private Date dueTime;

    private Integer itemCount;

    /** 1-进行中，2-已结束 */
    private Integer status;
}
