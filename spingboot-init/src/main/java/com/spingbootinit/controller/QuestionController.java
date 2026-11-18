package com.spingbootinit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.Result;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.context.UserContext.IsAdminRoleString;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.model.dto.question.*;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.questionvo.QuestionAdminListItemVO;
import com.spingbootinit.model.vo.questionvo.QuestionPublicDetailVO;
import com.spingbootinit.model.vo.questionvo.QuestionRunVO;
import com.spingbootinit.model.vo.questionvo.StudyRecommendationVO;
import com.spingbootinit.service.*;
import com.spingbootinit.service.impl.QuestionAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;


/**
 * 题目接口
 */
@RestController
@RequestMapping("/question")
@Slf4j
@Tag(name="题目接口",description = "题目接口")
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionRunService questionRunService;

    @Resource
    private StudyRecommendationService studyRecommendationService;

    @Resource
    private QuestionPreflightService questionPreflightService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionAuthorizationService questionAuthorizationService;

    /**
     * 判断是否是管理员
     */
    @Resource
    private IsAdminRoleString isAdminRole;
    // region 增删改查
    /**
     * 创建
     * @param questionAddRequest
     * @return
     */
    @Operation(summary = "创建题目",description = "创建题目")
    @PostMapping("/add")
    public Result<String> addQuestion(@Valid @RequestBody QuestionAddRequest questionAddRequest) {
        // 校验权限
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        requireImagePreflight(questionAddRequest);
        // 创建题目的逻辑
        long newQuestionId = questionService.createQuestion(questionAddRequest, currentUserId);
        return Result.success(String.valueOf(newQuestionId));
    }


    @Operation(summary = "管理员分页查询题目", description = "支持按标题、内容、标签、出题人筛选")
    @PostMapping("/admin/page")
    public Result<Page<QuestionAdminListItemVO>> adminPageQuestions(@RequestBody QuestionQueryRequest queryRequest) {
        // 校验权限
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        User currentUser = userService.getById(currentUserId);
        if (currentUser == null || !isAdminRole.isAdminRoleString(currentUser.getUserRole())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        // 查询题目的逻辑
        Page<QuestionAdminListItemVO> voPage = questionService.pageSelect(queryRequest);
        return Result.success(voPage);
    }

    @Operation(summary = "命题人分页查询题目", description = "老师只能查看自己的题目，管理员可查看全部")
    @PostMapping("/manage/page")
    public Result<Page<QuestionAdminListItemVO>> managePageQuestions(@RequestBody QuestionQueryRequest queryRequest) {
        Long currentUserId = requireCurrentUserId();
        return Result.success(questionService.pageSelectManaged(queryRequest, currentUserId));
    }

    @Operation(summary = "公开分页查询题目", description = "题库分页列表（无需管理员）")
    @PostMapping("/page")
    public Result<Page<QuestionAdminListItemVO>> pageQuestions(@RequestBody QuestionQueryRequest queryRequest) {
        Page<QuestionAdminListItemVO> voPage = questionService.pageSelectPublic(queryRequest);
        return Result.success(voPage);
    }

    @Operation(summary = "公开获取题目详情", description = "用于题库进入作答页")
    @GetMapping("/public/get/{id}")
    public Result<QuestionPublicDetailVO> getPublicQuestionDetail(@PathVariable("id") Long id) {
        // 获取题目详情的逻辑
        QuestionPublicDetailVO vo = questionService.getByIdPublicDetail(id);
        return Result.success(vo);
    }

    @Operation(summary = "根据我的提交记录推荐练习题", description = "返回知识点掌握度、今日训练进度与推荐题目")
    @GetMapping("/recommend/my")
    public Result<StudyRecommendationVO> recommendForMe() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        return Result.success(studyRecommendationService.recommend(userId));
    }

    @Operation(summary = "管理员保存前验题")
    @PostMapping("/admin/preflight")
    public Result<QuestionPreflightService.PreflightResult> preflight(@Valid @RequestBody QuestionAddRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        User user = userService.getById(userId);
        if (user == null || !isAdminRole.isAdminRoleString(user.getUserRole())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        return Result.success(questionPreflightService.check(request));
    }

    @Operation(summary = "命题人保存前验题")
    @PostMapping("/manage/preflight")
    public Result<QuestionPreflightService.PreflightResult> managePreflight(@Valid @RequestBody QuestionAddRequest request) {
        Long userId = requireCurrentUserId();
        questionAuthorizationService.requireAuthor(userId);
        return Result.success(questionPreflightService.check(request));
    }

    @Operation(summary = "运行代码（样例）", description = "调用沙箱执行当前代码与题目公开样例（最多 3 组），不入库、不计榜")
    @PostMapping("/run")
    public Result<QuestionRunVO> runQuestionSample(@Valid @RequestBody QuestionRunRequest request) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        QuestionRunVO vo = questionRunService.runSample(request);
        return Result.success(vo);
    }

    @Operation(summary = "管理员更新题目", description = "按 id 更新题目")
    @PostMapping("/admin/update")
    public Result<String> adminUpdateQuestion(@Valid @RequestBody QuestionUpdateRequest request) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        User currentUser = userService.getById(currentUserId);
        if (currentUser == null || !isAdminRole.isAdminRoleString(currentUser.getUserRole())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        QuestionAddRequest candidate = new QuestionAddRequest();
        BeanUtils.copyProperties(request, candidate);
        requireImagePreflight(candidate);
        // 更新题目的逻辑
        questionService.updateByIdQuestion(request);
        return Result.success("更新成功");
    }

    private void requireImagePreflight(QuestionAddRequest request) {
        if (request.getQuestionType() == null || !request.getQuestionType().startsWith("IMAGE_")) return;
        Long userId = UserContext.getCurrentUserId();
        User user = userId == null ? null : userService.getById(userId);
        if (user == null || !isAdminRole.isAdminRoleString(user.getUserRole())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        QuestionPreflightService.PreflightResult result = questionPreflightService.check(request);
        if (!result.ready()) {
            String reason = result.checks().stream().filter(check -> !check.passed())
                    .map(QuestionPreflightService.Check::detail).findFirst().orElse("请先完成图像题验题");
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "图像题发布前验题未通过：" + reason);
        }
    }

    @Operation(summary = "命题人更新题目")
    @PostMapping("/manage/update")
    public Result<String> manageUpdateQuestion(@Valid @RequestBody QuestionUpdateRequest request) {
        Long currentUserId = requireCurrentUserId();
        questionService.updateByIdQuestion(request, currentUserId);
        return Result.success("更新成功");
    }

    @Operation(summary = "管理员批量删除题目", description = "支持单个与批量删除")
    @PostMapping("/admin/delete/batch")
    public Result<Boolean> adminBatchDeleteQuestions(@Valid @RequestBody QuestionBatchDeleteRequest request) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        }
        User currentUser = userService.getById(currentUserId);
        if (currentUser == null || !isAdminRole.isAdminRoleString(currentUser.getUserRole())) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
        // 批量删除题目的逻辑
        questionService.removeBatchByIdsQuestion(request);
        return Result.success(true);
    }

    @Operation(summary = "命题人批量删除题目")
    @PostMapping("/manage/delete/batch")
    public Result<Boolean> manageBatchDeleteQuestions(@Valid @RequestBody QuestionBatchDeleteRequest request) {
        Long currentUserId = requireCurrentUserId();
        questionService.removeBatchByIdsQuestion(request, currentUserId);
        return Result.success(true);
    }

    private Long requireCurrentUserId() {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) throw new BusinessException(ResultCode.NOT_LOGIN_ERROR);
        return currentUserId;
    }



}
