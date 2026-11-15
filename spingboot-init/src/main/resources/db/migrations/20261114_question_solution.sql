-- 题解模块生产库升级脚本（MySQL 8）
-- 执行前请先备份数据库。本脚本只新增表，不删除原有数据。

CREATE TABLE IF NOT EXISTS `question_solution` (
    `id` BIGINT NOT NULL COMMENT '题解 id',
    `question_id` BIGINT NOT NULL COMMENT '题目 id',
    `user_id` BIGINT NOT NULL COMMENT '作者 id',
    `title` VARCHAR(160) NOT NULL COMMENT '标题',
    `summary` VARCHAR(500) NOT NULL COMMENT '列表摘要',
    `hint_content` MEDIUMTEXT NOT NULL COMMENT '未通过用户可见的安全提示',
    `content` MEDIUMTEXT NOT NULL COMMENT '完整 Markdown 题解',
    `review_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1待审核 2已发布 3已驳回',
    `review_reason` VARCHAR(500) DEFAULT NULL COMMENT '驳回原因',
    `reviewed_by` BIGINT DEFAULT NULL COMMENT '审核管理员',
    `reviewed_at` DATETIME DEFAULT NULL COMMENT '审核时间',
    `is_official` TINYINT NOT NULL DEFAULT 0 COMMENT '0普通 1官方',
    `view_count` INT NOT NULL DEFAULT 0,
    `like_count` INT NOT NULL DEFAULT 0,
    `comment_count` INT NOT NULL DEFAULT 0,
    `published_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_delete` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_qs_question_author` (`question_id`, `user_id`),
    KEY `idx_qs_public_list` (`question_id`, `review_status`, `is_official`, `create_time`),
    KEY `idx_qs_review_queue` (`review_status`, `create_time`),
    KEY `idx_qs_author` (`user_id`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题解';

CREATE TABLE IF NOT EXISTS `question_solution_like` (
    `id` BIGINT NOT NULL,
    `solution_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_qsl_solution_user` (`solution_id`, `user_id`),
    KEY `idx_qsl_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题解点赞';

CREATE TABLE IF NOT EXISTS `question_solution_comment` (
    `id` BIGINT NOT NULL,
    `solution_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `parent_id` BIGINT DEFAULT NULL,
    `content` VARCHAR(2000) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_delete` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_qsc_thread` (`solution_id`, `parent_id`, `create_time`),
    KEY `idx_qsc_user` (`user_id`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题解评论';
