-- Three-role permission model: user(student), teacher, admin.
-- Run after taking a database backup. Unknown role values are intentionally not changed.

UPDATE `user` SET `user_role` = 'admin'
WHERE `user_role` = '管理员';

UPDATE `user` SET `user_role` = 'user'
WHERE `user_role` IN ('普通用户', '学生', 'student');

UPDATE `user` SET `user_role` = 'teacher'
WHERE `user_role` IN ('教师', '老师');

CREATE TABLE IF NOT EXISTS `teacher_student_relation` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `teacher_id` BIGINT NOT NULL COMMENT '教师用户 ID',
    `student_id` BIGINT NOT NULL COMMENT '学生用户 ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1-有效 0-已解除',
    `assigned_by` BIGINT NOT NULL COMMENT '分配操作的管理员 ID',
    `assigned_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后分配时间',
    `unassigned_at` DATETIME NULL COMMENT '最后解除时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_delete` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_teacher_student` (`teacher_id`, `student_id`),
    KEY `idx_relation_teacher_active` (`teacher_id`, `status`, `is_delete`),
    KEY `idx_relation_student_active` (`student_id`, `status`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师学生分配关系';

SELECT `user_role`, COUNT(*) AS `role_count`
FROM `user`
WHERE `is_delete` = 0
GROUP BY `user_role`;
