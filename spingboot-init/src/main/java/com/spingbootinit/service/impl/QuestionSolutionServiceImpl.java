package com.spingbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.mapper.QuestionSolutionCommentMapper;
import com.spingbootinit.mapper.QuestionSolutionLikeMapper;
import com.spingbootinit.mapper.QuestionSolutionMapper;
import com.spingbootinit.model.dto.question.solution.SolutionOfficialRequest;
import com.spingbootinit.model.dto.question.solution.SolutionPageRequest;
import com.spingbootinit.model.dto.question.solution.SolutionReviewRequest;
import com.spingbootinit.model.dto.question.solution.SolutionSaveRequest;
import com.spingbootinit.model.entity.QuestionSolution;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.question.solution.SolutionDetailVO;
import com.spingbootinit.model.vo.question.solution.SolutionListItemVO;
import com.spingbootinit.service.QuestionService;
import com.spingbootinit.service.QuestionSolutionAccessService;
import com.spingbootinit.service.QuestionSolutionService;
import com.spingbootinit.service.UserService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestionSolutionServiceImpl extends ServiceImpl<QuestionSolutionMapper, QuestionSolution>
        implements QuestionSolutionService {

    private static final Pattern SUMMARY_CODE_START = Pattern.compile(
            "(?i)```|(?:#\\s*)?include\\s*[<\\\"]|\\busing\\s+namespace\\s+std\\s*;|\\b(?:int|signed)\\s+main\\s*\\(");

    static String readableSummary(String summary) {
        String text = StringUtils.trimToEmpty(summary);
        Matcher code = SUMMARY_CODE_START.matcher(text);
        if (code.find()) text = text.substring(0, code.start());
        text = text.replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? "这篇题解包含完整的解题思路与参考代码" : text;
    }

    @Resource private QuestionSolutionAccessService accessService;
    @Resource private QuestionService questionService;
    @Resource private UserService userService;
    @Resource private QuestionSolutionLikeMapper solutionLikeMapper;
    @Resource private QuestionSolutionCommentMapper solutionCommentMapper;

    static long parseId(String raw, String field) {
        if (StringUtils.isBlank(raw)) throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), field + "无效");
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), field + "格式错误");
        }
    }

    static void requireWrite(QuestionSolutionAccessService.AccessDecision access) {
        if (access == null || !access.canWrite()) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR.getCode(), "通过本题后才能编写题解");
        }
    }

    static void validateContent(SolutionSaveRequest request) {
        if (request == null) throw new BusinessException(ResultCode.PARAMS_ERROR);
        String title = StringUtils.trimToEmpty(request.getTitle());
        String summary = StringUtils.trimToEmpty(request.getSummary());
        String hint = StringUtils.trimToEmpty(request.getHintContent());
        String content = StringUtils.trimToEmpty(request.getContent());
        if (title.length() < 2 || title.length() > 160) throw new BusinessException(40000, "题解标题需要 2-160 个字符");
        if (summary.length() < 5 || summary.length() > 500) throw new BusinessException(40000, "题解摘要需要 5-500 个字符");
        if (hint.length() < 10 || hint.length() > 5000) throw new BusinessException(40000, "安全提示需要 10-5000 个字符");
        if (content.length() < 20 || content.length() > 100_000) throw new BusinessException(40000, "完整题解需要 20-100000 个字符");
    }

    static void applySave(QuestionSolution solution, SolutionSaveRequest request, boolean newRecord) {
        validateContent(request);
        solution.setTitle(request.getTitle().trim());
        solution.setSummary(readableSummary(request.getSummary()));
        solution.setHintContent(request.getHintContent().trim());
        solution.setContent(request.getContent().trim());
        if (newRecord) {
            solution.setReviewStatus(QuestionSolution.DRAFT);
        } else if (Objects.equals(solution.getReviewStatus(), QuestionSolution.PUBLISHED)
                || Objects.equals(solution.getReviewStatus(), QuestionSolution.PENDING)) {
            solution.setReviewStatus(QuestionSolution.PENDING);
        } else if (Objects.equals(solution.getReviewStatus(), QuestionSolution.REJECTED)) {
            solution.setReviewStatus(QuestionSolution.DRAFT);
        }
        solution.setReviewReason(null);
        solution.setReviewedBy(null);
        solution.setReviewedAt(null);
        solution.setIsOfficial(0);
    }

    static SolutionListItemVO toListItem(QuestionSolution solution) {
        SolutionListItemVO vo = new SolutionListItemVO();
        vo.setId(String.valueOf(solution.getId()));
        vo.setQuestionId(String.valueOf(solution.getQuestionId()));
        vo.setUserId(String.valueOf(solution.getUserId()));
        vo.setTitle(solution.getTitle());
        vo.setSummary(readableSummary(solution.getSummary()));
        vo.setHintContent(solution.getHintContent());
        vo.setReviewStatus(solution.getReviewStatus());
        vo.setOfficial(Objects.equals(solution.getIsOfficial(), 1));
        vo.setViewCount(solution.getViewCount());
        vo.setLikeCount(solution.getLikeCount());
        vo.setCommentCount(solution.getCommentCount());
        vo.setPublishedAt(solution.getPublishedAt());
        vo.setCreateTime(solution.getCreateTime());
        return vo;
    }

    static SolutionDetailVO toDetail(QuestionSolution solution, boolean full, boolean mine, Boolean liked) {
        SolutionListItemVO base = toListItem(solution);
        SolutionDetailVO vo = new SolutionDetailVO();
        vo.setId(base.getId()); vo.setQuestionId(base.getQuestionId()); vo.setUserId(base.getUserId());
        vo.setTitle(base.getTitle()); vo.setSummary(base.getSummary()); vo.setHintContent(base.getHintContent());
        vo.setReviewStatus(base.getReviewStatus()); vo.setOfficial(base.getOfficial());
        vo.setViewCount(base.getViewCount()); vo.setLikeCount(base.getLikeCount()); vo.setCommentCount(base.getCommentCount());
        vo.setPublishedAt(base.getPublishedAt()); vo.setCreateTime(base.getCreateTime());
        vo.setAccessLevel(full ? "FULL" : "HINT");
        vo.setContent(full ? solution.getContent() : null);
        vo.setReviewReason(mine ? solution.getReviewReason() : null);
        vo.setLiked(liked);
        vo.setMine(mine);
        vo.setUpdateTime(solution.getUpdateTime());
        return vo;
    }

    private void fillAuthor(SolutionListItemVO vo, Long userId) {
        User user = userService.getById(userId);
        if (user == null) { vo.setAuthorName("用户"); return; }
        vo.setAuthorName(StringUtils.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername());
        vo.setAuthorAvatar(user.getAvatar());
    }

    @Override
    public Page<SolutionListItemVO> pagePublic(SolutionPageRequest request) {
        long questionId = parseId(request.getQuestionId(), "题目 id");
        long current = Math.max(1, request.getCurrent());
        long size = Math.min(30, Math.max(1, request.getPageSize()));
        LambdaQueryWrapper<QuestionSolution> query = new LambdaQueryWrapper<QuestionSolution>()
                .eq(QuestionSolution::getQuestionId, questionId)
                .eq(QuestionSolution::getReviewStatus, QuestionSolution.PUBLISHED)
                .eq(QuestionSolution::getIsDelete, 0)
                .and(StringUtils.isNotBlank(request.getKeyword()), q -> q.like(QuestionSolution::getTitle, request.getKeyword().trim())
                        .or().like(QuestionSolution::getSummary, request.getKeyword().trim()))
                .orderByDesc(QuestionSolution::getIsOfficial)
                .orderByDesc(QuestionSolution::getPublishedAt);
        Page<QuestionSolution> rows = this.page(new Page<>(current, size), query);
        Page<SolutionListItemVO> result = new Page<>(current, size, rows.getTotal());
        List<SolutionListItemVO> records = rows.getRecords().stream().map(item -> {
            SolutionListItemVO vo = toListItem(item); fillAuthor(vo, item.getUserId()); return vo;
        }).toList();
        result.setRecords(records);
        return result;
    }

    @Override
    public Page<SolutionListItemVO> pageAdmin(SolutionPageRequest request, long adminId) {
        if (!accessService.decide(adminId, 1L).admin()) throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        long current = Math.max(1, request.getCurrent());
        long size = Math.min(50, Math.max(1, request.getPageSize()));
        LambdaQueryWrapper<QuestionSolution> query = new LambdaQueryWrapper<QuestionSolution>()
                .eq(request.getReviewStatus() != null, QuestionSolution::getReviewStatus, request.getReviewStatus())
                .eq(StringUtils.isNotBlank(request.getQuestionId()), QuestionSolution::getQuestionId,
                        StringUtils.isBlank(request.getQuestionId()) ? null : parseId(request.getQuestionId(), "题目 id"))
                .orderByAsc(QuestionSolution::getReviewStatus).orderByAsc(QuestionSolution::getCreateTime);
        Page<QuestionSolution> rows = this.page(new Page<>(current, size), query);
        Page<SolutionListItemVO> result = new Page<>(current, size, rows.getTotal());
        result.setRecords(rows.getRecords().stream().map(item -> { var vo = toListItem(item); fillAuthor(vo, item.getUserId()); return vo; }).toList());
        return result;
    }

    @Override
    public SolutionDetailVO getAdminDetail(long id, long adminId) {
        if (!accessService.decide(adminId, 1L).admin()) throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        QuestionSolution solution = this.getById(id);
        if (solution == null) throw new BusinessException(ResultCode.NOT_FOUND_ERROR);
        SolutionDetailVO vo = toDetail(solution, true, Objects.equals(solution.getUserId(), adminId), null);
        fillAuthor(vo, solution.getUserId());
        return vo;
    }

    @Override
    @Transactional
    public SolutionDetailVO getPublicDetail(long id, Long viewerId) {
        QuestionSolution solution = this.getById(id);
        if (solution == null || !Objects.equals(solution.getReviewStatus(), QuestionSolution.PUBLISHED))
            throw new BusinessException(ResultCode.NOT_FOUND_ERROR.getCode(), "题解不存在或未发布");
        var access = accessService.decide(viewerId, solution.getQuestionId());
        this.lambdaUpdate().eq(QuestionSolution::getId, id).setSql("view_count = view_count + 1").update();
        solution.setViewCount((solution.getViewCount() == null ? 0 : solution.getViewCount()) + 1);
        SolutionDetailVO vo = toDetail(solution, access.canViewFull(), false, null);
        fillAuthor(vo, solution.getUserId());
        return vo;
    }

    @Override
    public SolutionDetailVO getMine(long questionId, long userId) {
        requireWrite(accessService.decide(userId, questionId));
        QuestionSolution solution = this.lambdaQuery().eq(QuestionSolution::getQuestionId, questionId)
                .eq(QuestionSolution::getUserId, userId).one();
        if (solution == null) return null;
        SolutionDetailVO vo = toDetail(solution, true, true, null);
        fillAuthor(vo, userId);
        return vo;
    }

    @Override
    @Transactional
    public long saveSolution(SolutionSaveRequest request, long userId) {
        long questionId = parseId(request.getQuestionId(), "题目 id");
        requireWrite(accessService.decide(userId, questionId));
        if (questionService.getById(questionId) == null) throw new BusinessException(ResultCode.NOT_FOUND_ERROR.getCode(), "题目不存在");
        QuestionSolution solution = null;
        if (StringUtils.isNotBlank(request.getId())) {
            solution = this.getById(parseId(request.getId(), "题解 id"));
            if (solution == null || !Objects.equals(solution.getUserId(), userId)) throw new BusinessException(ResultCode.NO_AUTH_ERROR.getCode(), "无权修改该题解");
            if (!Objects.equals(solution.getQuestionId(), questionId)) throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "题目不一致");
        } else {
            solution = this.lambdaQuery().eq(QuestionSolution::getQuestionId, questionId).eq(QuestionSolution::getUserId, userId).one();
        }
        boolean fresh = solution == null;
        if (fresh) {
            solution = new QuestionSolution(); solution.setQuestionId(questionId); solution.setUserId(userId);
            solution.setViewCount(0); solution.setLikeCount(0); solution.setCommentCount(0); solution.setIsDelete(0);
        }
        applySave(solution, request, fresh);
        boolean ok = fresh ? this.save(solution) : this.updateById(solution);
        if (!ok) throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "题解保存失败");
        return solution.getId();
    }

    @Override
    @Transactional
    public boolean submitReview(long id, long userId) {
        QuestionSolution solution = this.getById(id);
        if (solution == null || !Objects.equals(solution.getUserId(), userId)) throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        requireWrite(accessService.decide(userId, solution.getQuestionId()));
        SolutionSaveRequest check = new SolutionSaveRequest();
        check.setTitle(solution.getTitle()); check.setSummary(solution.getSummary()); check.setHintContent(solution.getHintContent()); check.setContent(solution.getContent());
        validateContent(check);
        solution.setReviewStatus(QuestionSolution.PENDING); solution.setReviewReason(null); solution.setReviewedBy(null); solution.setReviewedAt(null); solution.setIsOfficial(0);
        return this.updateById(solution);
    }

    @Override
    @Transactional
    public boolean deleteMine(long id, long userId) {
        QuestionSolution solution = this.getById(id);
        if (solution == null || !Objects.equals(solution.getUserId(), userId)) throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        solutionLikeMapper.deleteBySolutionId(id);
        solutionCommentMapper.deleteBySolutionId(id);
        return this.baseMapper.hardDelete(id) > 0;
    }

    @Override
    @Transactional
    public boolean review(SolutionReviewRequest request, long adminId) {
        if (!accessService.decide(adminId, 1L).admin()) throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        long id = parseId(request.getId(), "题解 id");
        QuestionSolution solution = this.getById(id);
        if (solution == null) throw new BusinessException(ResultCode.NOT_FOUND_ERROR);
        if (!Objects.equals(solution.getReviewStatus(), QuestionSolution.PENDING)) throw new BusinessException(40000, "只能审核待审核题解");
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        String reason = StringUtils.trimToNull(request.getReason());
        if (!approved && reason == null) throw new BusinessException(40000, "驳回时请填写原因");
        solution.setReviewStatus(approved ? QuestionSolution.PUBLISHED : QuestionSolution.REJECTED);
        solution.setReviewReason(approved ? null : reason);
        solution.setReviewedBy(adminId); solution.setReviewedAt(new Date());
        solution.setPublishedAt(approved ? new Date() : null);
        if (!approved) solution.setIsOfficial(0);
        return this.updateById(solution);
    }

    @Override
    public boolean setOfficial(SolutionOfficialRequest request, long adminId) {
        if (!accessService.decide(adminId, 1L).admin()) throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        QuestionSolution solution = this.getById(parseId(request.getId(), "题解 id"));
        if (solution == null || !Objects.equals(solution.getReviewStatus(), QuestionSolution.PUBLISHED))
            throw new BusinessException(40000, "只能标记已发布题解");
        solution.setIsOfficial(Boolean.TRUE.equals(request.getOfficial()) ? 1 : 0);
        return this.updateById(solution);
    }
}
