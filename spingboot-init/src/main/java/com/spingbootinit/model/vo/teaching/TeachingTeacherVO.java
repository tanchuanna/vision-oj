package com.spingbootinit.model.vo.teaching;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 学生视角的老师信息（仅展示必要的公开资料与在线状态）。
 */
@Data
public class TeachingTeacherVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    /** 是否在线（基于心跳 TTL） */
    private Boolean online;
}
