package com.spingbootinit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.Result;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.model.dto.question.solution.*;
import com.spingbootinit.model.vo.question.solution.*;
import com.spingbootinit.service.QuestionSolutionAccessService;
import com.spingbootinit.service.QuestionSolutionInteractionService;
import com.spingbootinit.service.QuestionSolutionService;
import com.spingbootinit.utils.JwtUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/question-solution")
public class QuestionSolutionController {
    @Resource QuestionSolutionService solutionService;
    @Resource QuestionSolutionAccessService accessService;
    @Resource QuestionSolutionInteractionService interactionService;
    @Resource JwtUtils jwtUtils;

    private Long optionalUser(String authorization) {
        if (StringUtils.isBlank(authorization) || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = authorization.substring(7).trim();
        if (token.isEmpty() || !jwtUtils.validateToken(token)) return null;
        return jwtUtils.getUserIdFromToken(token);
    }

    private long currentUser() {
        Long uid = UserContext.getCurrentUserId();
        if (uid == null) throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        return uid;
    }

    private long id(String raw, String field) {
        try { long value = Long.parseLong(StringUtils.trimToEmpty(raw)); if (value <= 0) throw new NumberFormatException(); return value; }
        catch (NumberFormatException e) { throw new BusinessException(40000, field + "格式错误"); }
    }

    @GetMapping("/public/eligibility")
    public Result<SolutionEligibilityVO> eligibility(
            @RequestParam String questionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(accessService.eligibility(optionalUser(authorization), id(questionId, "题目 id")));
    }

    @PostMapping("/public/page")
    public Result<Page<SolutionListItemVO>> page(@RequestBody SolutionPageRequest request) {
        return Result.success(solutionService.pagePublic(request));
    }

    @GetMapping("/public/get")
    public Result<SolutionDetailVO> detail(
            @RequestParam String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(solutionService.getPublicDetail(id(id, "题解 id"), optionalUser(authorization)));
    }

    @GetMapping("/mine")
    public Result<SolutionDetailVO> mine(@RequestParam String questionId) {
        return Result.success(solutionService.getMine(id(questionId, "题目 id"), currentUser()));
    }

    @PostMapping("/save")
    public Result<String> save(@RequestBody SolutionSaveRequest request) {
        return Result.success(String.valueOf(solutionService.saveSolution(request, currentUser())));
    }

    @PostMapping("/submit-review")
    public Result<Boolean> submitReview(@RequestBody SolutionIdRequest request) {
        return Result.success(solutionService.submitReview(id(request.getId(), "题解 id"), currentUser()));
    }

    @PostMapping("/delete")
    public Result<Boolean> delete(@RequestBody SolutionIdRequest request) {
        return Result.success(solutionService.deleteMine(id(request.getId(), "题解 id"), currentUser()));
    }

    @PostMapping("/like/toggle")
    public Result<SolutionLikeVO> toggleLike(@RequestBody SolutionIdRequest request) {
        return Result.success(interactionService.toggleLike(id(request.getId(), "题解 id"), currentUser()));
    }

    @GetMapping("/comment/page")
    public Result<Page<SolutionCommentVO>> comments(
            @RequestParam String solutionId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(interactionService.pageComments(id(solutionId, "题解 id"), currentUser(), current, pageSize));
    }

    @PostMapping("/comment/add")
    public Result<String> addComment(@RequestBody SolutionCommentAddRequest request) {
        Long parentId = StringUtils.isBlank(request.getParentId()) ? null : id(request.getParentId(), "父评论 id");
        return Result.success(interactionService.addComment(id(request.getSolutionId(), "题解 id"), currentUser(), parentId, request.getContent()));
    }

    @PostMapping("/comment/delete")
    public Result<Boolean> deleteComment(@RequestBody SolutionIdRequest request) {
        return Result.success(interactionService.deleteComment(id(request.getId(), "评论 id"), currentUser()));
    }

    @PostMapping("/admin/page")
    public Result<Page<SolutionListItemVO>> adminPage(@RequestBody SolutionPageRequest request) {
        return Result.success(solutionService.pageAdmin(request, currentUser()));
    }

    @GetMapping("/admin/get")
    public Result<SolutionDetailVO> adminDetail(@RequestParam String id) {
        return Result.success(solutionService.getAdminDetail(id(id, "题解 id"), currentUser()));
    }

    @PostMapping("/admin/review")
    public Result<Boolean> review(@RequestBody SolutionReviewRequest request) {
        return Result.success(solutionService.review(request, currentUser()));
    }

    @PostMapping("/admin/official")
    public Result<Boolean> official(@RequestBody SolutionOfficialRequest request) {
        return Result.success(solutionService.setOfficial(request, currentUser()));
    }
}
