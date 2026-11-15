package com.spingbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("teacher_student_relation")
public class TeacherStudentRelation extends BaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long teacherId;
    private Long studentId;
    /** 0 unassigned, 1 active. */
    private Integer status;
    private Long assignedBy;
    private Date assignedAt;
    private Date unassignedAt;
}
