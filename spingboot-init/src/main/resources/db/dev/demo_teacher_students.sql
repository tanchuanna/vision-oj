-- Local development data only. Do not execute in production.
-- Login password for every account below: 123456
-- The hash is valid when app.security.pepper is empty (the local default).

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `teacher_student_relation` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `teacher_id` BIGINT NOT NULL COMMENT '教师用户 ID',
    `student_id` BIGINT NOT NULL COMMENT '学生用户 ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1-有效 0-已解除',
    `assigned_by` BIGINT NOT NULL COMMENT '分配操作的管理员 ID',
    `assigned_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `unassigned_at` DATETIME NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_delete` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_teacher_student` (`teacher_id`, `student_id`),
    KEY `idx_relation_teacher_active` (`teacher_id`, `status`, `is_delete`),
    KEY `idx_relation_student_active` (`student_id`, `status`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师学生关系';

INSERT IGNORE INTO `user` (
    `id`, `username`, `password`, `email`, `nickname`, `sex`, `user_role`,
    `status`, `points`, `check_in_count`, `last_read_announcement_id`,
    `create_time`, `update_time`, `is_delete`
) VALUES
    (1999991000000000001, 'teacher01', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'teacher01@myoj.local', '张老师', 1, 'teacher', 1, 180, 12, 0, NOW(), NOW(), 0),
    (1999991000000000002, 'teacher02', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'teacher02@myoj.local', '李老师', 2, 'teacher', 1, 150, 9, 0, NOW(), NOW(), 0),
    (1999991000000000003, 'teacher03', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'teacher03@myoj.local', '王老师', 1, 'teacher', 1, 210, 16, 0, NOW(), NOW(), 0),
    (1999991000000000004, 'teacher04', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'teacher04@myoj.local', '陈老师', 2, 'teacher', 1, 130, 7, 0, NOW(), NOW(), 0),
    (1999992000000000001, 'student01', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student01@myoj.local', '林晨', 1, 'user', 1, 320, 22, 0, NOW(), NOW(), 0),
    (1999992000000000002, 'student02', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student02@myoj.local', '周雨', 2, 'user', 1, 285, 19, 0, NOW(), NOW(), 0),
    (1999992000000000003, 'student03', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student03@myoj.local', '吴桐', 1, 'user', 1, 260, 17, 0, NOW(), NOW(), 0),
    (1999992000000000004, 'student04', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student04@myoj.local', '郑可', 2, 'user', 1, 245, 16, 0, NOW(), NOW(), 0),
    (1999992000000000005, 'student05', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student05@myoj.local', '黄宇', 1, 'user', 1, 230, 15, 0, NOW(), NOW(), 0),
    (1999992000000000006, 'student06', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student06@myoj.local', '赵清', 2, 'user', 1, 215, 14, 0, NOW(), NOW(), 0),
    (1999992000000000007, 'student07', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student07@myoj.local', '孙浩', 1, 'user', 1, 200, 13, 0, NOW(), NOW(), 0),
    (1999992000000000008, 'student08', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student08@myoj.local', '钱悦', 2, 'user', 1, 188, 12, 0, NOW(), NOW(), 0),
    (1999992000000000009, 'student09', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student09@myoj.local', '许航', 1, 'user', 1, 176, 11, 0, NOW(), NOW(), 0),
    (1999992000000000010, 'student10', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student10@myoj.local', '何宁', 2, 'user', 1, 164, 10, 0, NOW(), NOW(), 0),
    (1999992000000000011, 'student11', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student11@myoj.local', '罗川', 1, 'user', 1, 152, 10, 0, NOW(), NOW(), 0),
    (1999992000000000012, 'student12', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student12@myoj.local', '梁月', 2, 'user', 1, 140, 9, 0, NOW(), NOW(), 0),
    (1999992000000000013, 'student13', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student13@myoj.local', '宋阳', 1, 'user', 1, 128, 8, 0, NOW(), NOW(), 0),
    (1999992000000000014, 'student14', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student14@myoj.local', '谢琳', 2, 'user', 1, 116, 7, 0, NOW(), NOW(), 0),
    (1999992000000000015, 'student15', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student15@myoj.local', '唐峰', 1, 'user', 1, 104, 7, 0, NOW(), NOW(), 0),
    (1999992000000000016, 'student16', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student16@myoj.local', '韩雪', 2, 'user', 1, 92, 6, 0, NOW(), NOW(), 0),
    (1999992000000000017, 'student17', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student17@myoj.local', '冯越', 1, 'user', 1, 80, 5, 0, NOW(), NOW(), 0),
    (1999992000000000018, 'student18', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student18@myoj.local', '邓琪', 2, 'user', 1, 68, 4, 0, NOW(), NOW(), 0),
    (1999992000000000019, 'student19', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student19@myoj.local', '曹睿', 1, 'user', 1, 56, 3, 0, NOW(), NOW(), 0),
    (1999992000000000020, 'student20', '$2b$12$G9mY/PieJFSciBfogd1dWOXTo4NOk8LdMhlwGceXVw3qrbDvUCJje', 'student20@myoj.local', '彭佳', 2, 'user', 1, 44, 2, 0, NOW(), NOW(), 0);

INSERT IGNORE INTO `teacher_student_relation`
    (`id`, `teacher_id`, `student_id`, `status`, `assigned_by`, `assigned_at`, `create_time`, `update_time`, `is_delete`)
VALUES
    (2999991000000000001, 1999991000000000001, 1999992000000000001, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000002, 1999991000000000001, 1999992000000000002, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000003, 1999991000000000001, 1999992000000000003, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000004, 1999991000000000001, 1999992000000000004, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000005, 1999991000000000001, 1999992000000000005, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000006, 1999991000000000002, 1999992000000000006, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000007, 1999991000000000002, 1999992000000000007, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000008, 1999991000000000002, 1999992000000000008, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000009, 1999991000000000002, 1999992000000000009, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000010, 1999991000000000002, 1999992000000000010, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000011, 1999991000000000003, 1999992000000000011, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000012, 1999991000000000003, 1999992000000000012, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000013, 1999991000000000003, 1999992000000000013, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000014, 1999991000000000003, 1999992000000000014, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000015, 1999991000000000003, 1999992000000000015, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000016, 1999991000000000004, 1999992000000000016, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000017, 1999991000000000004, 1999992000000000017, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000018, 1999991000000000004, 1999992000000000018, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000019, 1999991000000000004, 1999992000000000019, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0),
    (2999991000000000020, 1999991000000000004, 1999992000000000020, 1, 1999990000000000001, NOW(), NOW(), NOW(), 0);

SELECT `user_role`, COUNT(*) AS `account_count`
FROM `user`
WHERE `is_delete` = 0
GROUP BY `user_role`;

