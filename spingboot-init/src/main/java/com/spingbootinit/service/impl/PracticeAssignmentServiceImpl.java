package com.spingbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.mapper.PracticeAssignmentItemMapper;
import com.spingbootinit.mapper.PracticeAssignmentMapper;
import com.spingbootinit.model.dto.teaching.PracticeAssignRequest;
import com.spingbootinit.model.entity.*;
import com.spingbootinit.model.vo.teaching.PracticeAssignResultVO;
import com.spingbootinit.model.vo.teaching.PracticeAssignmentVO;
import com.spingbootinit.model.vo.teaching.PracticeItemVO;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 复用个性化推荐引擎，把推荐结果固化成老师可追踪的练习。
 */
@Service
@RequiredArgsConstructor
public class PracticeAssignmentServiceImpl implements PracticeAssignmentService {
    private static final int MAX_STUDENTS = 50;
    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final int MAX_QUESTION_COUNT = 10;
    private static final int TEACHER_LIST_LIMIT = 100;
    private static final int STUDENT_LIST_LIMIT = 50;
    private static final int SUBMIT_SCAN_LIMIT = 5000;
    private static final Set<String> DIFFICULTIES = Set.of("入门", "简单", "中等", "困难");

    private final PracticeAssignmentMapper assignmentMapper;
    private final PracticeAssignmentItemMapper itemMapper;
    private final StudyRecommendationService studyRecommendationService;
    private final TeacherStudentService teacherStudentService;
    private final QuestionService questionService;
    private final QuestionSubmitService questionSubmitService;
    private final UserService userService;
    private final UserRolePolicy rolePolicy;

    @Override
    public PracticeAssignResultVO assign(long operatorId, String operatorRole, PracticeAssignRequest request) {
        if (request == null || request.getStudentIds() == null || request.getStudentIds().isEmpty()) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "请至少选择一名学生");
        }
        boolean admin = rolePolicy.isAdmin(operatorRole);
        if (!admin && !rolePolicy.isTeacher(operatorRole)) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        LinkedHashSet<Long> studentIds = request.getStudentIds().stream()
                .filter(Objects::nonNull)
                .limit(MAX_STUDENTS)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (studentIds.isEmpty()) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "请至少选择一名学生");
        }

        int questionCount = request.getQuestionCount() == null
                ? DEFAULT_QUESTION_COUNT
                : Math.max(1, Math.min(MAX_QUESTION_COUNT, request.getQuestionCount()));
        String title = StringUtils.hasText(request.getTitle())
                ? request.getTitle().trim()
                : "个性化练习 · " + LocalDate.now();
        title = trimTo(title, 120);
        String remark = trimTo(request.getRemark(), 500);

        List<PracticeAssignment> created = new ArrayList<>();
        List<PracticeAssignmentItem> createdItems = new ArrayList<>();
        List<Long> skippedIds = new ArrayList<>();

        for (Long studentId : studentIds) {
            if (!admin && !teacherStudentService.canTeacherAccessStudent(operatorId, studentId)) {
                skippedIds.add(studentId);
                continue;
            }
            List<StudyRecommendationEngine.Recommendation> recommendations =
                    studyRecommendationService.recommendFor(studentId, questionCount);
            if (recommendations.isEmpty()) {
                skippedIds.add(studentId);
                continue;
            }
            PracticeAssignment assignment = new PracticeAssignment();
            assignment.setTeacherId(operatorId);
            assignment.setStudentId(studentId);
            assignment.setTitle(title);
            assignment.setRemark(remark);
            assignment.setDueTime(request.getDueTime());
            assignment.setItemCount(recommendations.size());
            assignment.setStatus(1);
            assignmentMapper.insert(assignment);

            int order = 1;
            for (StudyRecommendationEngine.Recommendation recommendation : recommendations) {
                PracticeAssignmentItem item = new PracticeAssignmentItem();
                item.setAssignmentId(assignment.getId());
                item.setQuestionId(recommendation.questionId());
                item.setSortOrder(order++);
                item.setFocusTag(trimTo(recommendation.focusTag(), 64));
                item.setReason(trimTo(recommendation.reason(), 255));
                createdItems.add(item);
            }
            created.add(assignment);
        }
        for (PracticeAssignmentItem item : createdItems) {
            itemMapper.insert(item);
        }

        PracticeAssignResultVO result = new PracticeAssignResultVO();
        result.setTitle(title);
        result.setCreated(created.size());
        result.setSkipped(skippedIds.size());
        result.setTotalQuestions(createdItems.size());
        result.setSkippedStudentNames(displayNames(skippedIds));
        return result;
    }

    @Override
    public List<PracticeAssignmentVO> listForTeacher(long operatorId, String operatorRole, Long studentId) {
        boolean admin = rolePolicy.isAdmin(operatorRole);
        if (!admin && !rolePolicy.isTeacher(operatorRole)) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        LambdaQueryWrapper<PracticeAssignment> wrapper = new LambdaQueryWrapper<PracticeAssignment>()
                .eq(studentId != null, PracticeAssignment::getStudentId, studentId)
                .eq(!admin, PracticeAssignment::getTeacherId, operatorId)
                .orderByDesc(PracticeAssignment::getCreateTime)
                .last("LIMIT " + TEACHER_LIST_LIMIT);
        return enrich(assignmentMapper.selectList(wrapper));
    }

    @Override
    public List<PracticeAssignmentVO> listForStudent(long studentId) {
        LambdaQueryWrapper<PracticeAssignment> wrapper = new LambdaQueryWrapper<PracticeAssignment>()
                .eq(PracticeAssignment::getStudentId, studentId)
                .orderByDesc(PracticeAssignment::getCreateTime)
                .last("LIMIT " + STUDENT_LIST_LIMIT);
        return enrich(assignmentMapper.selectList(wrapper));
    }

    @Override
    public void delete(long operatorId, String operatorRole, long assignmentId) {
        PracticeAssignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND_ERROR.getCode(), "练习不存在或已删除");
        }
        if (!rolePolicy.isAdmin(operatorRole) && !Objects.equals(assignment.getTeacherId(), operatorId)) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        assignmentMapper.deleteById(assignmentId);
        itemMapper.delete(new LambdaQueryWrapper<PracticeAssignmentItem>()
                .eq(PracticeAssignmentItem::getAssignmentId, assignmentId));
    }

    private List<PracticeAssignmentVO> enrich(List<PracticeAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        List<Long> assignmentIds = assignments.stream().map(PracticeAssignment::getId).toList();
        List<PracticeAssignmentItem> items = itemMapper.selectList(new LambdaQueryWrapper<PracticeAssignmentItem>()
                .in(PracticeAssignmentItem::getAssignmentId, assignmentIds)
                .orderByAsc(PracticeAssignmentItem::getSortOrder));
        Map<Long, List<PracticeAssignmentItem>> itemsByAssignment = items.stream()
                .collect(Collectors.groupingBy(PracticeAssignmentItem::getAssignmentId));

        Set<Long> questionIds = items.stream().map(PracticeAssignmentItem::getQuestionId).collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionIds.isEmpty() ? Map.of()
                : questionService.listByIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        Set<Long> userIds = new HashSet<>();
        for (PracticeAssignment assignment : assignments) {
            userIds.add(assignment.getStudentId());
            userIds.add(assignment.getTeacherId());
        }
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<String, Date> acceptedAt = loadAcceptedAt(assignments, questionIds);

        List<PracticeAssignmentVO> result = new ArrayList<>(assignments.size());
        for (PracticeAssignment assignment : assignments) {
            PracticeAssignmentVO vo = new PracticeAssignmentVO();
            vo.setId(assignment.getId());
            vo.setTeacherId(assignment.getTeacherId());
            vo.setStudentId(assignment.getStudentId());
            vo.setTitle(assignment.getTitle());
            vo.setRemark(assignment.getRemark());
            vo.setDueTime(assignment.getDueTime());
            vo.setCreateTime(assignment.getCreateTime());
            vo.setItemCount(assignment.getItemCount());
            vo.setStatus(assignment.getStatus());
            vo.setStudentName(displayName(userMap.get(assignment.getStudentId())));
            vo.setTeacherName(displayName(userMap.get(assignment.getTeacherId())));

            int solvedCount = 0;
            for (PracticeAssignmentItem item : itemsByAssignment.getOrDefault(assignment.getId(), List.of())) {
                Question question = questionMap.get(item.getQuestionId());
                Date acceptedTime = acceptedAt.get(assignment.getStudentId() + "#" + item.getQuestionId());
                boolean solved = acceptedTime != null && assignment.getCreateTime() != null
                        && !acceptedTime.before(assignment.getCreateTime());
                if (solved) {
                    solvedCount++;
                }
                PracticeItemVO itemVO = new PracticeItemVO();
                itemVO.setQuestionId(item.getQuestionId());
                itemVO.setTitle(question == null ? "题目已删除" : question.getTitle());
                itemVO.setQuestionType(question == null ? null : question.getQuestionType());
                itemVO.setDifficulty(difficultyOf(question == null ? null : question.getTags()));
                itemVO.setFocusTag(item.getFocusTag());
                itemVO.setReason(item.getReason());
                itemVO.setSortOrder(item.getSortOrder());
                itemVO.setSolved(solved);
                vo.getItems().add(itemVO);
            }
            vo.getItems().sort(Comparator.comparing(PracticeItemVO::getSortOrder,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            vo.setSolvedCount(solvedCount);
            int itemCount = assignment.getItemCount() == null ? vo.getItems().size() : assignment.getItemCount();
            vo.setProgress(itemCount <= 0 ? 0 : (int) Math.round(100.0 * solvedCount / itemCount));
            result.add(vo);
        }
        return result;
    }

    private Map<String, Date> loadAcceptedAt(List<PracticeAssignment> assignments, Set<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> studentIds = assignments.stream().map(PracticeAssignment::getStudentId).collect(Collectors.toSet());
        Date earliest = assignments.stream().map(PracticeAssignment::getCreateTime)
                .filter(Objects::nonNull).min(Date::compareTo).orElse(null);
        LambdaQueryWrapper<QuestionSubmit> wrapper = new LambdaQueryWrapper<QuestionSubmit>()
                .select(QuestionSubmit::getUserId, QuestionSubmit::getQuestionId,
                        QuestionSubmit::getJudgeInfo, QuestionSubmit::getCreateTime)
                .in(QuestionSubmit::getUserId, studentIds)
                .in(QuestionSubmit::getQuestionId, questionIds)
                .eq(QuestionSubmit::getStatus, 2)
                .last("LIMIT " + SUBMIT_SCAN_LIMIT);
        if (earliest != null) {
            wrapper.ge(QuestionSubmit::getCreateTime, earliest);
        }
        Map<String, Date> acceptedAt = new HashMap<>();
        for (QuestionSubmit submit : questionSubmitService.list(wrapper)) {
            if (!isAccepted(submit.getJudgeInfo()) || submit.getCreateTime() == null) {
                continue;
            }
            acceptedAt.merge(submit.getUserId() + "#" + submit.getQuestionId(), submit.getCreateTime(),
                    (left, right) -> left.before(right) ? left : right);
        }
        return acceptedAt;
    }

    private boolean isAccepted(String judgeInfo) {
        try {
            return "Accepted".equalsIgnoreCase(JSONUtil.parseObj(judgeInfo).getStr("message"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String difficultyOf(List<String> tags) {
        if (tags == null) {
            return null;
        }
        for (String tag : tags) {
            if (tag != null && DIFFICULTIES.contains(tag)) {
                return tag;
            }
        }
        return null;
    }

    private List<String> displayNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return userIds.stream().map(id -> displayName(users.get(id))).toList();
    }

    private String displayName(User user) {
        if (user == null) {
            return "未知用户";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername() : "未知用户";
    }

    private String trimTo(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
