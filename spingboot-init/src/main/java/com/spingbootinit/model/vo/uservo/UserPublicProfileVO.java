package com.spingbootinit.model.vo.uservo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.spingbootinit.model.dto.user.Location;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 公开用户资料：用于查看他人主页，不含邮箱、手机等敏感信息。
 */
@Data
public class UserPublicProfileVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String nickname;
    private String username;
    private String avatar;
    private String introduction;
    private List<String> website;
    private Location locationCodes;

    /** 0-未知，1-男，2-女 */
    private Integer sex;

    private String country;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date birthday;

    /** 0-禁用 1-正常 */
    private Integer status;

    private String userRole;

    /** 积分（签到等行为累计） */
    private Integer points;

    /** 累计签到天数 */
    private Integer checkInCount;

    /** 注册时间 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date createTime;

    /** 通过（AC）题目数（去重） */
    private Long solvedCount;

    /** 累计提交次数 */
    private Long submitCount;

    /** 学生的指导老师昵称列表 */
    private List<String> teacherNames;

    /** 老师名下的学生数 */
    private Long studentCount;
}
