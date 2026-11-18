package com.spingbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 练习题目明细；是否完成由提交记录实时计算，不落库。
 */
@TableName("practice_assignment_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class PracticeAssignmentItem extends BaseEntity implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private Long assignmentId;

    private Long questionId;

    private Integer sortOrder;

    private String focusTag;

    private String reason;
}
