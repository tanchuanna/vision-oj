package com.spingbootinit.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSolutionSchemaTest {

    private static final Path SCHEMA = Path.of("src/main/resources/db/schema.sql");
    private static final Path MIGRATION = Path.of("src/main/resources/db/migrations/20261114_question_solution.sql");

    @Test
    void masterSchemaAndMigrationContainCompleteQuestionSolutionTables() throws IOException {
        assertSchema(Files.readString(SCHEMA));
        assertTrue(Files.exists(MIGRATION), "missing production migration SQL");
        assertSchema(Files.readString(MIGRATION));
    }

    private static void assertSchema(String sql) {
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `question_solution`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `question_solution_like`"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `question_solution_comment`"));
        assertTrue(sql.contains("`hint_content` MEDIUMTEXT"));
        assertTrue(sql.contains("`review_status` TINYINT"));
        assertTrue(sql.contains("`review_reason` VARCHAR(500)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_qs_question_author` (`question_id`, `user_id`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_qsl_solution_user` (`solution_id`, `user_id`)"));
        assertTrue(sql.contains("KEY `idx_qs_public_list` (`question_id`, `review_status`, `is_official`, `create_time`)"));
    }
}
