package com.spingbootinit.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.Result;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.context.UserContext.IsAdminRoleString;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.spingbootinit.model.dto.questionsubmit.SubmissionDashboardQuery;
import com.spingbootinit.model.entity.QuestionSubmit;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.questionsubmitvo.*;
import com.spingbootinit.service.QuestionSubmitService;
import com.spingbootinit.service.SubmissionDashboardService;
import com.spingbootinit.service.UserService;
import com.spingbootinit.service.impl.SubmissionAccessScopeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目提交接口
 */
@RestController
@RequestMapping("/question_submit")
@Slf4j
public class QuestionSubmitController {

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private UserService userService;

    @Resource
    private IsAdminRoleString isAdminRole;

    @Resource
    private SubmissionDashboardService submissionDashboardService;

    @Resource
    private SubmissionAccessScopeService submissionAccessScopeService;

    @GetMapping("/page")
    public Result<Page<SubmissionListItemVO>> pageSubmissions(
            @RequestParam(value = "scope", defaultValue = "SELF") String scope,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "questionKeyword", required = false) String questionKeyword,
            @RequestParam(value = "userKeyword", required = false) String userKeyword,
            @RequestParam(value = "verdict", required = false) String verdict,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "sort", defaultValue = "DESC") String sort,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "pageSize", defaultValue = "15") long pageSize) {
        long currentUserId = requireLogin();
        var access = submissionAccessScopeService.resolve(currentUserId, scope);
        SubmissionDashboardQuery query = dashboardQuery(access, userId, questionKeyword, userKeyword, verdict,
                language, startTime, endTime, sort, current, pageSize);
        return Result.success(submissionDashboardService.page(query));
    }

    @GetMapping("/overview")
    public Result<SubmissionOverviewVO> getSubmissionOverview(
            @RequestParam(value = "scope", defaultValue = "SELF") String scope,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "questionKeyword", required = false) String questionKeyword,
            @RequestParam(value = "userKeyword", required = false) String userKeyword,
            @RequestParam(value = "verdict", required = false) String verdict,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "days", defaultValue = "30") int days) {
        long currentUserId = requireLogin();
        var access = submissionAccessScopeService.resolve(currentUserId, scope);
        SubmissionDashboardQuery query = dashboardQuery(access, userId, questionKeyword, userKeyword, verdict,
                language, startTime, endTime, "DESC", 1, 15);
        return Result.success(submissionDashboardService.overview(query, days,
                submissionAccessScopeService.isAdmin(currentUserId)));
    }

    @GetMapping("/{submissionId}/detail")
    public Result<SubmissionDetailVO> getSubmissionDetail(@PathVariable("submissionId") Long submissionId) {
        long currentUserId = requireLogin();
        if (submissionId == null || submissionId <= 0) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "提交 ID 必须为正整数");
        }
        var access = submissionAccessScopeService.resolveForDetail(currentUserId);
        return Result.success(submissionDashboardService.getDetail(
                submissionId, currentUserId, access.allManagedUsers(), access.managedUserIds()));
    }

    private long requireLogin() {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        return currentUserId;
    }

    private SubmissionDashboardQuery dashboardQuery(SubmissionAccessScopeService.Access access,
                                                     Long userId,
                                                     String questionKeyword, String userKeyword,
                                                     String verdict, String language,
                                                     LocalDateTime startTime, LocalDateTime endTime,
                                                     String sort, long current, long pageSize) {
        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "开始时间必须早于结束时间");
        }
        SubmissionDashboardQuery query = new SubmissionDashboardQuery();
        query.setOwnerUserId(access.ownerUserId());
        query.setManaged(access.managed());
        query.setAllManagedUsers(access.allManagedUsers());
        query.setManagedUserIds(access.managedUserIds());
        if (userId != null) {
            if (userId <= 0) {
                throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "用户 ID 必须为正整数");
            }
            if (!access.managed()
                    || (!access.allManagedUsers() && !access.managedUserIds().contains(userId))) {
                throw new BusinessException(ResultCode.NO_AUTH_ERROR.getCode(), "无权查看该学生的提交记录");
            }
            query.setUserId(userId);
        }
        query.setQuestionKeyword(questionKeyword);
        query.setUserKeyword(userKeyword);
        query.setVerdict(verdict);
        query.setLanguage(language);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setSort(sort);
        query.setCurrent(current);
        query.setPageSize(pageSize);
        return query;
    }

    /**
     * 当前题目的公开统计，游客可直接访问且不包含个人信息。
     */
    @GetMapping("/public/statistics")
    public Result<QuestionStatisticsVO> getPublicQuestionStatistics(@RequestParam("questionId") Long questionId) {
        if (questionId == null || questionId <= 0) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "题目 ID 必须为正整数");
        }
        return Result.success(questionSubmitService.getQuestionStatistics(questionId, null));
    }

    /** 登录用户版本：在公开统计基础上补充个人 AC 与击败比例。 */
    @GetMapping("/statistics/my")
    public Result<QuestionStatisticsVO> getMyQuestionStatistics(@RequestParam("questionId") Long questionId) {
        if (questionId == null || questionId <= 0) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "题目 ID 必须为正整数");
        }
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        return Result.success(questionSubmitService.getQuestionStatistics(questionId, currentUserId));
    }

    /**
     * 提交题目
     *
     * @return 含题目 id、本题次序 submitNo、全站次序 globalSubmitNo（与「全部提交」列表行一致）；管理员额外返回 dbId。轮询用 GET /question_submit/status/my。
     */
    @PostMapping("/")
    public Result<QuestionSubmitCreatedVO> doQuestionSubmit(@RequestBody QuestionSubmitAddRequest questionSubmitAddRequest) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        QuestionSubmit submit = questionSubmitService.QuestionSubmit(questionSubmitAddRequest, currentUserId);
        QuestionSubmitCreatedVO vo = new QuestionSubmitCreatedVO();
        vo.setQuestionId(submit.getQuestionId());
        vo.setSubmitNo(questionSubmitService.computeSubmitSerial(submit, true));
        vo.setGlobalSubmitNo(questionSubmitService.computeSubmitSerial(submit, false));
        User me = userService.getById(currentUserId);
        if (me != null && isAdminRole.isAdminRoleString(me.getUserRole())) {
            vo.setDbId(submit.getId() == null ? null : String.valueOf(submit.getId()));
        }
        return Result.success(vo);
    }

    /**
     * 按「该题第几次提交」查询状态（推荐：响应体不含数据库提交主键）。
     */
    @GetMapping("/status/my")
    public Result<MySubmitItemVO> getSubmitStatusBySubmitNo(
            @RequestParam("questionId") Long questionId,
            @RequestParam("submitNo") Long submitNo
    ) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        return Result.success(questionSubmitService.getMySubmitStatusBySubmitNo(currentUserId, questionId, submitNo));
    }

    /**
     * 按数据库主键查询单次提交状态（兼容旧前端；响应体仍不返回 id，请用 submitNo 展示）。
     */
    @GetMapping("/status/{id}")
    public Result<MySubmitItemVO> getSubmitStatus(@PathVariable("id") Long submitId) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        return Result.success(questionSubmitService.getMySubmitStatus(submitId, currentUserId));
    }

    @GetMapping("/my/page/all")
    public Result<Page<MySubmitItemVO>> pageMyAllQuestionSubmits(
            @RequestParam(value = "submitNo", required = false) Long submitNo,
            @RequestParam(value = "questionId", required = false) Long questionId,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize
    ) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        if (questionId != null && questionId <= 0) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "题目 ID 必须为正整数");
        }
        long pageNo = current <= 0 ? 1 : current;
        long size = pageSize <= 0 ? 10 : Math.min(pageSize, 50);

        if (submitNo != null && submitNo > 0) {
            QuestionSubmit one = questionSubmitService.findByUserGlobalSubmitNo(currentUserId, submitNo);
            if (one != null && questionId != null && questionId > 0 && !questionId.equals(one.getQuestionId())) {
                one = null;
            }
            if (one == null) {
                Page<MySubmitItemVO> empty = new Page<>(1, size, 0);
                empty.setRecords(List.of());
                return Result.success(empty);
            }
            MySubmitItemVO vo = questionSubmitService.toMySubmitItemVo(one, false, false);
            Page<MySubmitItemVO> single = new Page<>(1, size, 1);
            single.setRecords(List.of(vo));
            return Result.success(single);
        }

        var query = questionSubmitService.lambdaQuery()
                .eq(QuestionSubmit::getUserId, currentUserId);
        if (questionId != null && questionId > 0) {
            query.eq(QuestionSubmit::getQuestionId, questionId);
        }
        Page<QuestionSubmit> page = query
                .orderByAsc(QuestionSubmit::getCreateTime)
                .orderByAsc(QuestionSubmit::getId)
                .page(new Page<>(pageNo, size));

        List<MySubmitItemVO> records = page.getRecords().stream()
                .map(item -> questionSubmitService.toMySubmitItemVo(item, false, false))
                .toList();

        Page<MySubmitItemVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records);
        return Result.success(voPage);
    }

    @GetMapping("/admin/page")
    public Result<Page<MySubmitItemVO>> pageAllQuestionSubmitsForAdmin(
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "questionId", required = false) Long questionId,
            @RequestParam(value = "submitNo", required = false) Long submitNo
    ) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        User currentUser = userService.getById(currentUserId);
        if (currentUser == null || !isAdminRole.isAdminRoleString(currentUser.getUserRole())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        long pageNo = current <= 0 ? 1 : current;
        long size = pageSize <= 0 ? 10 : Math.min(pageSize, 100);

        if (submitNo != null && submitNo > 0) {
            if (userId == null || userId <= 0 || questionId == null || questionId <= 0) {
                throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "按次序筛选需同时填写用户 ID 与题目 ID");
            }
            QuestionSubmit one = questionSubmitService.findByUserQuestionAndSubmitNo(userId, questionId, submitNo);
            if (one == null) {
                Page<MySubmitItemVO> empty = new Page<>(1, size, 0);
                empty.setRecords(List.of());
                return Result.success(empty);
            }
            MySubmitItemVO vo = questionSubmitService.toMySubmitItemVo(one, true, true);
            Page<MySubmitItemVO> single = new Page<>(1, size, 1);
            single.setRecords(List.of(vo));
            return Result.success(single);
        }

        var query = questionSubmitService.lambdaQuery();
        if (userId != null && userId > 0) {
            query.eq(QuestionSubmit::getUserId, userId);
        }
        if (questionId != null && questionId > 0) {
            query.eq(QuestionSubmit::getQuestionId, questionId);
        }
        Page<QuestionSubmit> page = query
                .orderByAsc(QuestionSubmit::getCreateTime)
                .orderByAsc(QuestionSubmit::getId)
                .page(new Page<>(pageNo, size));

        List<MySubmitItemVO> records = page.getRecords().stream()
                .map(item -> questionSubmitService.toMySubmitItemVo(item, true, true))
                .toList();

        Page<MySubmitItemVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records);
        return Result.success(voPage);
    }

}
