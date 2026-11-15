-- 提交记录工作台：全站按删除状态和时间倒序查询索引（可重复执行）
DROP PROCEDURE IF EXISTS `mayue_add_submission_dashboard_index`;
DELIMITER $$
CREATE PROCEDURE `mayue_add_submission_dashboard_index`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'question_submit'
          AND index_name = 'idx_qs_deleted_time'
    ) THEN
        ALTER TABLE `question_submit`
            ADD INDEX `idx_qs_deleted_time` (`is_delete`, `create_time` DESC, `id` DESC);
    END IF;
END$$
DELIMITER ;
CALL `mayue_add_submission_dashboard_index`();
DROP PROCEDURE IF EXISTS `mayue_add_submission_dashboard_index`;
