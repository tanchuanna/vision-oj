package com.spingbootinit.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionStatisticsSchemaTest {

    private static final Path SCHEMA = Path.of("src/main/resources/db/schema.sql");
    private static final Path MIGRATION = Path.of("src/main/resources/db/migrations/V20261114__question_statistics.sql");

    @Test
    void schemaAndMigrationExposeIndexedQuestionStatisticsColumns() throws IOException {
        assertStatisticsSql(Files.readString(SCHEMA));
        assertTrue(Files.exists(MIGRATION), "missing question statistics migration SQL");
        assertStatisticsSql(Files.readString(MIGRATION));
    }

    private static void assertStatisticsSql(String sql) {
        sql = sql.replace("''", "'");
        assertTrue(sql.contains("`verdict` VARCHAR(32)"));
        assertTrue(sql.contains("JSON_UNQUOTE(JSON_EXTRACT(`judge_info`, '$.message'))"));
        assertTrue(sql.contains("`runtime_ms` BIGINT"));
        assertTrue(sql.contains("JSON_EXTRACT(`judge_info`, '$.time')"));
        assertTrue(sql.contains("`memory_kb` BIGINT"));
        assertTrue(sql.contains("JSON_EXTRACT(`judge_info`, '$.memory')"));
        assertTrue(sql.contains("`code_length` INT"));
        assertTrue(sql.contains("OCTET_LENGTH(`code`)"));
        assertTrue(sql.contains("idx_qs_fastest"));
        assertTrue(sql.contains("idx_qs_memory"));
    }
}
