-- 题目统计与排行榜升级脚本（MySQL 8）
-- 执行前请备份数据库。本脚本不删除数据，生成列会从现有 judge_info/code 自动计算。
USE `test`;

DROP PROCEDURE IF EXISTS `mayue_add_column_if_missing`;
DELIMITER $$
CREATE PROCEDURE `mayue_add_column_if_missing`(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL `mayue_add_column_if_missing`('question_submit', 'verdict',
    '`verdict` VARCHAR(32) GENERATED ALWAYS AS (IF(JSON_VALID(`judge_info`), JSON_UNQUOTE(JSON_EXTRACT(`judge_info`, ''$.message'')), NULL)) STORED COMMENT ''判题结果生成列'' AFTER `judge_info`');
CALL `mayue_add_column_if_missing`('question_submit', 'runtime_ms',
    '`runtime_ms` BIGINT GENERATED ALWAYS AS (IF(JSON_VALID(`judge_info`), CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(`judge_info`, ''$.time'')), ''null'') AS UNSIGNED), NULL)) STORED COMMENT ''运行耗时毫秒'' AFTER `verdict`');
CALL `mayue_add_column_if_missing`('question_submit', 'memory_kb',
    '`memory_kb` BIGINT GENERATED ALWAYS AS (IF(JSON_VALID(`judge_info`), CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(`judge_info`, ''$.memory'')), ''null'') AS UNSIGNED), NULL)) STORED COMMENT ''内存消耗 KB'' AFTER `runtime_ms`');
CALL `mayue_add_column_if_missing`('question_submit', 'code_length',
    '`code_length` INT GENERATED ALWAYS AS (OCTET_LENGTH(`code`)) STORED COMMENT ''代码字节数'' AFTER `memory_kb`');
DROP PROCEDURE IF EXISTS `mayue_add_column_if_missing`;

DROP PROCEDURE IF EXISTS `mayue_add_index_if_missing`;
DELIMITER $$
CREATE PROCEDURE `mayue_add_index_if_missing`(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL `mayue_add_index_if_missing`('question_submit', 'idx_qs_fastest',
    '(`question_id`, `is_delete`, `status`, `verdict`, `runtime_ms`, `user_id`)');
CALL `mayue_add_index_if_missing`('question_submit', 'idx_qs_memory',
    '(`question_id`, `is_delete`, `status`, `verdict`, `memory_kb`, `user_id`)');
DROP PROCEDURE IF EXISTS `mayue_add_index_if_missing`;
