package com.spingbootinit.model.vo.teaching;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

/**
 * 师生关系列表项（含对方公开信息与关系状态）。
 * status: 1=已建立, 2=待审批
 */
@Data
public class TeacherStudentRelationVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 对方用户 id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long peerId;

    private String peerUsername;
    private String peerNickname;
    private String peerAvatar;

    /** 1=已建立, 2=待审批 */
    private Integer status;

    /** 发起人 id，用于判断是学生申请还是老师邀请 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long initiatedBy;

    /** 当前用户是否为发起人 */
    private Boolean mine;

    /** 等待谁审批：teacher / student */
    private String pendingRole;

    private Date createTime;
    private Date assignedAt;
}
