package com.spingbootinit.model.vo.teaching;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 练习中的一道题。
 */
@Data
public class PracticeItemVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long questionId;
    private String title;
    private String questionType;
    private String difficulty;
    private String focusTag;
    private String reason;
    private Integer sortOrder;
    private Boolean solved;
}
