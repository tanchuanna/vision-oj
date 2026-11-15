package com.spingbootinit.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@TableName("question_solution")
public class QuestionSolution extends BaseEntity {
    public static final int DRAFT = 0;
    public static final int PENDING = 1;
    public static final int PUBLISHED = 2;
    public static final int REJECTED = 3;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long questionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String title;
    private String summary;
    private String hintContent;
    private String content;
    private Integer reviewStatus;
    private String reviewReason;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewedBy;
    private Date reviewedAt;
    private Integer isOfficial;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Date publishedAt;
}
