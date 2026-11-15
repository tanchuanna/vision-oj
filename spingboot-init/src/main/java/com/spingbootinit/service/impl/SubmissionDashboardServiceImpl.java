package com.spingbootinit.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.model.dto.questionsubmit.SubmissionDashboardQuery;
import com.spingbootinit.model.entity.Question;
import com.spingbootinit.model.entity.QuestionSubmit;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.questionsubmitvo.SubmissionDetailVO;
import com.spingbootinit.model.vo.questionsubmitvo.SubmissionListItemVO;
import com.spingbootinit.model.vo.questionsubmitvo.SubmissionOverviewVO;
import com.spingbootinit.service.QuestionService;
import com.spingbootinit.service.QuestionSubmitService;
import com.spingbootinit.service.SubmissionDashboardService;
import com.spingbootinit.service.UserService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SubmissionDashboardServiceImpl implements SubmissionDashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final NamedParameterJdbcTemplate jdbc;
    private final QuestionSubmitService submitService;
    private final QuestionService questionService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public SubmissionDashboardServiceImpl(NamedParameterJdbcTemplate jdbc,
                                          QuestionSubmitService submitService,
                                          QuestionService questionService,
                                          UserService userService,
                                          ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.submitService = submitService;
        this.questionService = questionService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<SubmissionListItemVO> page(SubmissionDashboardQuery query) {
        normalize(query);
        MapSqlParameterSource params = params(query);
        String where = where(query, params);
        long total = Optional.ofNullable(jdbc.queryForObject(
                "SELECT COUNT(*) FROM question_submit qs JOIN question q ON q.id=qs.question_id " +
                        "LEFT JOIN `user` u ON u.id=qs.user_id WHERE " + where,
                params, Long.class)).orElse(0L);
        params.addValue("limit", query.getPageSize());
        params.addValue("offset", (query.getCurrent() - 1) * query.getPageSize());
        String order = "ASC".equals(query.getSort()) ? "ASC" : "DESC";
        String sql = """
                SELECT CAST(qs.id AS CHAR) submission_id,
                  (SELECT COUNT(*) FROM question_submit qsn
                    WHERE qsn.is_delete=0 AND qsn.user_id=qs.user_id
                      AND (qsn.create_time < qs.create_time OR (qsn.create_time=qs.create_time AND qsn.id<=qs.id))) global_submit_no,
                  CAST(qs.question_id AS CHAR) question_id, q.title question_title,
                  CAST(qs.user_id AS CHAR) user_id, u.username, u.nickname, u.avatar,
                  qs.language, qs.status, qs.verdict, qs.runtime_ms, qs.memory_kb, qs.code_length,
                  CAST(qs.contest_id AS CHAR) contest_id, qs.create_time
                FROM question_submit qs
                JOIN question q ON q.id=qs.question_id
                LEFT JOIN `user` u ON u.id=qs.user_id
                WHERE %s
                ORDER BY qs.create_time %s, qs.id %s
                LIMIT :limit OFFSET :offset
                """.formatted(where, order, order);
        List<SubmissionListItemVO> records = jdbc.query(sql, params, (rs, rowNum) -> {
            SubmissionListItemVO vo = new SubmissionListItemVO();
            vo.setSubmissionId(rs.getString("submission_id"));
            vo.setGlobalSubmitNo(rs.getLong("global_submit_no"));
            vo.setQuestionId(rs.getString("question_id"));
            vo.setQuestionTitle(rs.getString("question_title"));
            if (query.isManaged()) {
                vo.setUserId(rs.getString("user_id"));
                vo.setUsername(rs.getString("username"));
                vo.setNickname(rs.getString("nickname"));
                vo.setAvatar(rs.getString("avatar"));
            }
            vo.setLanguage(rs.getString("language"));
            vo.setStatus(rs.getInt("status"));
            vo.setVerdict(rs.getString("verdict"));
            vo.setRuntimeMs(nullableLong(rs, "runtime_ms"));
            vo.setMemoryKb(nullableLong(rs, "memory_kb"));
            vo.setCodeLength(nullableInt(rs, "code_length"));
            vo.setContestId(rs.getString("contest_id"));
            vo.setCreateTime(rs.getTimestamp("create_time"));
            return vo;
        });
        Page<SubmissionListItemVO> page = new Page<>(query.getCurrent(), query.getPageSize(), total);
        page.setRecords(records);
        return page;
    }

    @Override
    public SubmissionOverviewVO overview(SubmissionDashboardQuery query, int days, boolean canManage) {
        normalize(query);
        int rangeDays = days == 7 || days == 90 ? days : 30;
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDateTime rangeStart = today.minusDays(rangeDays - 1L).atStartOfDay();
        if (query.getStartTime() == null || query.getStartTime().isBefore(rangeStart)) {
            query.setStartTime(rangeStart);
        }
        MapSqlParameterSource params = params(query);
        String where = where(query, params);
        String from = " FROM question_submit qs JOIN question q ON q.id=qs.question_id LEFT JOIN `user` u ON u.id=qs.user_id WHERE " + where;
        Map<String, Object> summary = jdbc.queryForMap("SELECT COUNT(*) total, " +
                "SUM(CASE WHEN qs.verdict='Accepted' THEN 1 ELSE 0 END) accepted, " +
                "COUNT(DISTINCT CASE WHEN qs.verdict='Accepted' THEN qs.question_id END) solved, " +
                "COUNT(DISTINCT qs.user_id) active_users, " +
                "SUM(CASE WHEN qs.status IN (0,1) THEN 1 ELSE 0 END) pending" + from, params);
        SubmissionOverviewVO vo = new SubmissionOverviewVO();
        vo.setTotalSubmissions(number(summary.get("total")));
        vo.setAcceptedSubmissions(number(summary.get("accepted")));
        vo.setSolvedProblems(number(summary.get("solved")));
        vo.setActiveUsers(number(summary.get("active_users")));
        vo.setPendingSubmissions(number(summary.get("pending")));
        vo.setAcceptedRate(vo.getTotalSubmissions() == 0 ? 0 :
                Math.round(vo.getAcceptedSubmissions() * 10000.0 / vo.getTotalSubmissions()) / 100.0);
        vo.setAvailableScopes(canManage ? List.of("SELF", "MANAGED") : List.of("SELF"));

        Map<String, SubmissionOverviewVO.TrendPoint> trend = new LinkedHashMap<>();
        for (int i = rangeDays - 1; i >= 0; i--) {
            String date = today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            trend.put(date, new SubmissionOverviewVO.TrendPoint(date, 0, 0));
        }
        jdbc.query("SELECT DATE(qs.create_time) day, COUNT(*) total, " +
                        "SUM(CASE WHEN qs.verdict='Accepted' THEN 1 ELSE 0 END) accepted" + from +
                        " GROUP BY DATE(qs.create_time) ORDER BY day", params, rs -> {
            String day = rs.getDate("day").toLocalDate().toString();
            trend.put(day, new SubmissionOverviewVO.TrendPoint(day, rs.getLong("total"), rs.getLong("accepted")));
        });
        vo.setTrend(new ArrayList<>(trend.values()));
        vo.setVerdictDistribution(distribution("COALESCE(NULLIF(qs.verdict,''),'Pending')", from, params));
        vo.setLanguageDistribution(distribution("COALESCE(NULLIF(LOWER(qs.language),''),'unknown')", from, params));
        return vo;
    }

    @Override
    public SubmissionDetailVO getDetail(long submissionId, long currentUserId, boolean admin, List<Long> managedUserIds) {
        QuestionSubmit submit = submitService.getById(submissionId);
        if (submit == null) {
            throw new BusinessException(ResultCode.NOT_FOUND_ERROR);
        }
        boolean assignedStudent = managedUserIds != null && managedUserIds.contains(submit.getUserId());
        if (!Objects.equals(submit.getUserId(), currentUserId) && !admin && !assignedStudent) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        Question question = questionService.getById(submit.getQuestionId());
        User user = userService.getById(submit.getUserId());
        JsonNode judge = readJudge(submit.getJudgeInfo());
        SubmissionDetailVO vo = new SubmissionDetailVO();
        vo.setSubmissionId(String.valueOf(submit.getId()));
        vo.setQuestionId(String.valueOf(submit.getQuestionId()));
        vo.setQuestionTitle(question == null ? "题目已删除" : question.getTitle());
        vo.setUserId(String.valueOf(submit.getUserId()));
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
        vo.setLanguage(submit.getLanguage());
        vo.setStatus(submit.getStatus());
        vo.setVerdict(text(judge, "message", statusVerdict(submit.getStatus())));
        vo.setRuntimeMs(longValue(judge, "time"));
        vo.setMemoryKb(longValue(judge, "memory"));
        vo.setCode(submit.getCode());
        vo.setCodeLength(submit.getCode() == null ? 0 : submit.getCode().length());
        vo.setErrorDetail(cleanError(text(judge, "detail", "")));
        vo.setContestId(submit.getContestId() == null ? null : String.valueOf(submit.getContestId()));
        vo.setCreateTime(submit.getCreateTime());
        vo.setOwnSubmission(Objects.equals(submit.getUserId(), currentUserId));
        return vo;
    }

    private List<SubmissionOverviewVO.DistributionItem> distribution(String expression, String from,
                                                                      MapSqlParameterSource params) {
        return jdbc.query("SELECT " + expression + " name, COUNT(*) value" + from +
                        " GROUP BY name ORDER BY value DESC", params,
                (rs, n) -> new SubmissionOverviewVO.DistributionItem(rs.getString("name"), rs.getLong("value")));
    }

    private void normalize(SubmissionDashboardQuery query) {
        query.setCurrent(Math.max(1, query.getCurrent()));
        query.setPageSize(Math.max(1, Math.min(50, query.getPageSize())));
        query.setSort("ASC".equalsIgnoreCase(query.getSort()) ? "ASC" : "DESC");
    }

    private MapSqlParameterSource params(SubmissionDashboardQuery q) {
        return new MapSqlParameterSource()
                .addValue("ownerUserId", q.getOwnerUserId())
                .addValue("managedUserIds", q.getManagedUserIds())
                .addValue("userId", q.getUserId())
                .addValue("questionKeyword", trim(q.getQuestionKeyword()))
                .addValue("userKeyword", trim(q.getUserKeyword()))
                .addValue("verdict", trim(q.getVerdict()))
                .addValue("language", trim(q.getLanguage()))
                .addValue("startTime", q.getStartTime() == null ? null : Timestamp.valueOf(q.getStartTime()))
                .addValue("endTime", q.getEndTime() == null ? null : Timestamp.valueOf(q.getEndTime()));
    }

    private String where(SubmissionDashboardQuery q, MapSqlParameterSource ignored) {
        List<String> clauses = new ArrayList<>(List.of("qs.is_delete=0", "q.is_delete=0"));
        if (q.getUserId() != null) clauses.add("qs.user_id=:userId");
        else if (q.getOwnerUserId() != null) clauses.add("qs.user_id=:ownerUserId");
        else if (q.isManaged() && !q.isAllManagedUsers()) {
            if (q.getManagedUserIds() == null || q.getManagedUserIds().isEmpty()) clauses.add("1=0");
            else clauses.add("qs.user_id IN (:managedUserIds)");
        }
        if (has(q.getQuestionKeyword())) clauses.add("(q.title LIKE CONCAT('%',:questionKeyword,'%') OR CAST(q.id AS CHAR)=:questionKeyword)");
        if (q.isManaged() && has(q.getUserKeyword())) clauses.add("(u.nickname LIKE CONCAT('%',:userKeyword,'%') OR u.username LIKE CONCAT('%',:userKeyword,'%') OR CAST(u.id AS CHAR)=:userKeyword)");
        if (has(q.getVerdict())) clauses.add("qs.verdict=:verdict");
        if (has(q.getLanguage())) clauses.add("LOWER(qs.language)=LOWER(:language)");
        if (q.getStartTime() != null) clauses.add("qs.create_time>=:startTime");
        if (q.getEndTime() != null) clauses.add("qs.create_time<:endTime");
        return String.join(" AND ", clauses);
    }

    private JsonNode readJudge(String json) {
        if (!has(json)) return objectMapper.createObjectNode();
        try { return objectMapper.readTree(json); }
        catch (Exception ignored) { return objectMapper.createObjectNode(); }
    }

    private String cleanError(String detail) {
        if (!has(detail)) return "";
        String cleaned = detail.replaceAll("(?i)(/tmp|/app|/www)[^\\s:]*", "[运行环境]")
                .replaceAll("(?i)(token|secret|password|authorization)\\s*[:=]\\s*[^\\s,;]+", "$1=[已隐藏]");
        return cleaned.length() > 4000 ? cleaned.substring(0, 4000) + "…" : cleaned;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        long value = rs.getLong(name); return rs.wasNull() ? null : value;
    }
    private static Integer nullableInt(java.sql.ResultSet rs, String name) throws java.sql.SQLException {
        int value = rs.getInt(name); return rs.wasNull() ? null : value;
    }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static boolean has(String value) { return value != null && !value.trim().isEmpty(); }
    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field); return value == null || value.isNull() ? fallback : value.asText(fallback);
    }
    private static Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field); return value == null || !value.isNumber() ? null : value.asLong();
    }
    private static String statusVerdict(Integer status) {
        if (status == null || status == 0) return "Pending";
        if (status == 1) return "Judging";
        return status == 2 ? "Accepted" : "Failed";
    }
}
