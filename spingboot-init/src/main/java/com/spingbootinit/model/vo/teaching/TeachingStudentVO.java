package com.spingbootinit.model.vo.teaching;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.util.Date;

/** Learning-only student projection. Deliberately excludes contact and security data. */
@Data
public class TeachingStudentVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer accountStatus;
    private Long submitCount;
    private Long acceptedCount;
    private Long solvedCount;
    private Double passRate;
    private Date lastActiveTime;
    /** 是否在线（基于心跳 TTL） */
    private Boolean online;
}
