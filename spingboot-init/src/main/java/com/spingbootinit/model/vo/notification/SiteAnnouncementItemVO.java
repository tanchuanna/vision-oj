package com.spingbootinit.model.vo.notification;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;

@Data
public class SiteAnnouncementItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    private String content;

    private Date publishedAt;

    /** 发布者昵称 */
    private String publisherName;

    /** 发布者角色：user / teacher / admin */
    private String publisherRole;
}
