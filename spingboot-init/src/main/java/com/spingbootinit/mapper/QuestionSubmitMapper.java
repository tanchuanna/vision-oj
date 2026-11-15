package com.spingbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spingbootinit.model.entity.QuestionSubmit;
import com.spingbootinit.model.vo.questionsubmitvo.QuestionPerformanceVO;
import com.spingbootinit.model.vo.questionsubmitvo.QuestionRankingItemVO;
import com.spingbootinit.model.vo.questionsubmitvo.QuestionStatisticsVO;
import com.spingbootinit.model.vo.teaching.TeachingStudentVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface QuestionSubmitMapper extends BaseMapper<QuestionSubmit> {

    @Select("""
            SELECT u.id, u.username, u.nickname, u.avatar, u.status AS accountStatus,
                   COUNT(qs.id) AS submitCount,
                   COALESCE(SUM(CASE WHEN qs.status = 2 AND qs.verdict = 'Accepted' THEN 1 ELSE 0 END), 0) AS acceptedCount,
                   COUNT(DISTINCT CASE WHEN qs.status = 2 AND qs.verdict = 'Accepted' THEN qs.question_id END) AS solvedCount,
                   CASE WHEN COUNT(qs.id) = 0 THEN 0
                        ELSE ROUND(100.0 * SUM(CASE WHEN qs.status = 2 AND qs.verdict = 'Accepted' THEN 1 ELSE 0 END) / COUNT(qs.id), 2)
                   END AS passRate,
                   MAX(qs.create_time) AS lastActiveTime
            FROM `user` u
            LEFT JOIN question_submit qs ON qs.user_id = u.id AND qs.is_delete = 0
            WHERE u.id = #{studentId} AND u.user_role = 'user' AND u.is_delete = 0
            GROUP BY u.id, u.username, u.nickname, u.avatar, u.status
            """)
    TeachingStudentVO selectTeachingStudent(@Param("studentId") long studentId);

    @Select("""
            SELECT COUNT(*) AS totalSubmissions,
                   COALESCE(SUM(CASE WHEN status = 2 AND verdict = 'Accepted' THEN 1 ELSE 0 END), 0) AS acceptedSubmissions,
                   COUNT(DISTINCT CASE WHEN status = 2 AND verdict = 'Accepted' THEN user_id END) AS acceptedUsers
            FROM question_submit
            WHERE question_id = #{questionId}
              AND is_delete = 0
            """)
    QuestionStatisticsVO selectQuestionStatistics(@Param("questionId") long questionId);

    @Select("""
            WITH user_best AS (
                SELECT qs.user_id, qs.language, qs.runtime_ms, qs.memory_kb, qs.code_length, qs.create_time, qs.id,
                       ROW_NUMBER() OVER (
                           PARTITION BY qs.user_id
                           ORDER BY qs.runtime_ms ASC, qs.memory_kb ASC, qs.code_length ASC, qs.create_time ASC, qs.id ASC
                       ) AS user_row
                FROM question_submit qs
                WHERE qs.question_id = #{questionId}
                  AND qs.is_delete = 0
                  AND qs.status = 2
                  AND qs.verdict = 'Accepted'
                  AND qs.runtime_ms IS NOT NULL
            ), ranked AS (
                SELECT ub.*,
                       ROW_NUMBER() OVER (
                           ORDER BY ub.runtime_ms ASC, ub.memory_kb ASC, ub.code_length ASC, ub.create_time ASC, ub.id ASC
                       ) AS rank_no
                FROM user_best ub
                WHERE ub.user_row = 1
            )
            SELECT CAST(r.rank_no AS SIGNED) AS rankNo,
                   COALESCE(NULLIF(u.nickname, ''), u.username) AS userName,
                   u.avatar AS userAvatar,
                   r.runtime_ms AS runtimeMs,
                   r.memory_kb AS memoryKb,
                   r.language,
                   r.code_length AS codeLength,
                   r.create_time AS createTime
            FROM ranked r
            INNER JOIN `user` u ON u.id = r.user_id AND u.is_delete = 0
            ORDER BY r.rank_no
            LIMIT #{limit}
            """)
    java.util.List<QuestionRankingItemVO> selectFastestRanking(
            @Param("questionId") long questionId,
            @Param("limit") int limit);

    @Select("""
            WITH user_best AS (
                SELECT qs.user_id, qs.language, qs.runtime_ms, qs.memory_kb, qs.code_length, qs.create_time, qs.id,
                       ROW_NUMBER() OVER (
                           PARTITION BY qs.user_id
                           ORDER BY qs.memory_kb ASC, qs.runtime_ms ASC, qs.code_length ASC, qs.create_time ASC, qs.id ASC
                       ) AS user_row
                FROM question_submit qs
                WHERE qs.question_id = #{questionId}
                  AND qs.is_delete = 0
                  AND qs.status = 2
                  AND qs.verdict = 'Accepted'
                  AND qs.memory_kb IS NOT NULL
            ), ranked AS (
                SELECT ub.*,
                       ROW_NUMBER() OVER (
                           ORDER BY ub.memory_kb ASC, ub.runtime_ms ASC, ub.code_length ASC, ub.create_time ASC, ub.id ASC
                       ) AS rank_no
                FROM user_best ub
                WHERE ub.user_row = 1
            )
            SELECT CAST(r.rank_no AS SIGNED) AS rankNo,
                   COALESCE(NULLIF(u.nickname, ''), u.username) AS userName,
                   u.avatar AS userAvatar,
                   r.runtime_ms AS runtimeMs,
                   r.memory_kb AS memoryKb,
                   r.language,
                   r.code_length AS codeLength,
                   r.create_time AS createTime
            FROM ranked r
            INNER JOIN `user` u ON u.id = r.user_id AND u.is_delete = 0
            ORDER BY r.rank_no
            LIMIT #{limit}
            """)
    java.util.List<QuestionRankingItemVO> selectMemoryRanking(
            @Param("questionId") long questionId,
            @Param("limit") int limit);

    @Select("""
            SELECT MIN(runtime_ms) AS runtimeMs,
                   MIN(memory_kb) AS memoryKb
            FROM question_submit
            WHERE question_id = #{questionId}
              AND user_id = #{userId}
              AND is_delete = 0
              AND status = 2
              AND verdict = 'Accepted'
            """)
    QuestionPerformanceVO selectUserBestPerformance(
            @Param("questionId") long questionId,
            @Param("userId") long userId);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT user_id, MIN(runtime_ms) AS best_runtime
                FROM question_submit
                WHERE question_id = #{questionId}
                  AND is_delete = 0
                  AND status = 2
                  AND verdict = 'Accepted'
                  AND runtime_ms IS NOT NULL
                GROUP BY user_id
            ) best
            WHERE best.best_runtime > #{runtimeMs}
            """)
    long countUsersSlowerThanRuntime(
            @Param("questionId") long questionId,
            @Param("runtimeMs") long runtimeMs);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT user_id, MIN(memory_kb) AS best_memory
                FROM question_submit
                WHERE question_id = #{questionId}
                  AND is_delete = 0
                  AND status = 2
                  AND verdict = 'Accepted'
                  AND memory_kb IS NOT NULL
                GROUP BY user_id
            ) best
            WHERE best.best_memory > #{memoryKb}
            """)
    long countUsersUsingMoreMemory(
            @Param("questionId") long questionId,
            @Param("memoryKb") long memoryKb);

    @Select("""
            SELECT EXISTS(
                SELECT 1
                FROM question_submit
                WHERE user_id = #{userId}
                  AND question_id = #{questionId}
                  AND status = 2
                  AND is_delete = 0
                  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(judge_info, '$.message')), '') = 'Accepted'
                LIMIT 1
            )
            """)
    boolean existsAccepted(@Param("userId") long userId, @Param("questionId") long questionId);
}
