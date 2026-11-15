package com.spingbootinit.model.dto.questionsubmit;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SubmissionDashboardQuery {
    private Long ownerUserId;
    private boolean managed;
    private boolean allManagedUsers;
    private List<Long> managedUserIds = List.of();
    /** 指定查看某个用户的提交（老师查看特定学生时使用，需在 managedUserIds 范围内） */
    private Long userId;
    private String questionKeyword;
    private String userKeyword;
    private String verdict;
    private String language;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String sort = "DESC";
    private long current = 1;
    private long pageSize = 15;
}
