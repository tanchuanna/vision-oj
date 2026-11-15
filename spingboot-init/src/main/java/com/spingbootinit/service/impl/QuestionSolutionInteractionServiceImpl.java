package com.spingbootinit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.mapper.QuestionSolutionCommentMapper;
import com.spingbootinit.mapper.QuestionSolutionLikeMapper;
import com.spingbootinit.mapper.QuestionSolutionMapper;
import com.spingbootinit.model.entity.QuestionSolution;
import com.spingbootinit.model.entity.QuestionSolutionComment;
import com.spingbootinit.model.entity.QuestionSolutionLike;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.model.vo.question.solution.SolutionCommentVO;
import com.spingbootinit.model.vo.question.solution.SolutionLikeVO;
import com.spingbootinit.service.QuestionSolutionAccessService;
import com.spingbootinit.service.QuestionSolutionInteractionService;
import com.spingbootinit.service.UserService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class QuestionSolutionInteractionServiceImpl implements QuestionSolutionInteractionService {
    @Resource QuestionSolutionMapper solutionMapper;
    @Resource QuestionSolutionLikeMapper likeMapper;
    @Resource QuestionSolutionCommentMapper commentMapper;
    @Resource QuestionSolutionAccessService accessService;
    @Resource UserService userService;

    private QuestionSolution requirePublishedWithFullAccess(long solutionId, long userId) {
        QuestionSolution solution = solutionMapper.selectById(solutionId);
        if (solution == null || !Objects.equals(solution.getReviewStatus(), QuestionSolution.PUBLISHED))
            throw new BusinessException(ResultCode.NOT_FOUND_ERROR.getCode(), "题解不存在或未发布");
        if (!accessService.decide(userId, solution.getQuestionId()).canViewFull())
            throw new BusinessException(ResultCode.NO_AUTH_ERROR.getCode(), "通过本题后才能参与题解讨论");
        return solution;
    }

    @Override
    @Transactional
    public SolutionLikeVO toggleLike(long solutionId, long userId) {
        QuestionSolution solution = requirePublishedWithFullAccess(solutionId, userId);
        LambdaQueryWrapper<QuestionSolutionLike> query = new LambdaQueryWrapper<QuestionSolutionLike>()
                .eq(QuestionSolutionLike::getSolutionId, solutionId).eq(QuestionSolutionLike::getUserId, userId);
        QuestionSolutionLike existing = likeMapper.selectOne(query);
        boolean liked;
        if (existing != null) {
            likeMapper.delete(query); solutionMapper.changeLikeCount(solutionId, -1); liked = false;
        } else {
            QuestionSolutionLike like = new QuestionSolutionLike(); like.setSolutionId(solutionId); like.setUserId(userId);
            try { likeMapper.insert(like); solutionMapper.changeLikeCount(solutionId, 1); liked = true; }
            catch (DuplicateKeyException ignored) { liked = true; }
        }
        QuestionSolution refreshed = solutionMapper.selectById(solutionId);
        int count = refreshed == null || refreshed.getLikeCount() == null ? 0 : refreshed.getLikeCount();
        return new SolutionLikeVO(liked, count);
    }

    @Override
    public Page<SolutionCommentVO> pageComments(long solutionId, long userId, long current, long size) {
        requirePublishedWithFullAccess(solutionId, userId);
        Page<QuestionSolutionComment> page = commentMapper.selectPage(
                new Page<>(Math.max(1, current), Math.min(50, Math.max(1, size))),
                new LambdaQueryWrapper<QuestionSolutionComment>()
                        .eq(QuestionSolutionComment::getSolutionId, solutionId)
                        .orderByAsc(QuestionSolutionComment::getCreateTime));
        Page<SolutionCommentVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(item -> toComment(item, userId)).toList());
        return result;
    }

    private SolutionCommentVO toComment(QuestionSolutionComment item, long viewerId) {
        SolutionCommentVO vo = new SolutionCommentVO();
        vo.setId(String.valueOf(item.getId())); vo.setSolutionId(String.valueOf(item.getSolutionId()));
        vo.setUserId(String.valueOf(item.getUserId())); vo.setParentId(item.getParentId() == null ? null : String.valueOf(item.getParentId()));
        vo.setContent(item.getContent()); vo.setCreateTime(item.getCreateTime()); vo.setMine(Objects.equals(item.getUserId(), viewerId));
        User user = userService.getById(item.getUserId());
        if (user != null) { vo.setAuthorName(StringUtils.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername()); vo.setAuthorAvatar(user.getAvatar()); }
        else vo.setAuthorName("用户");
        return vo;
    }

    @Override
    @Transactional
    public String addComment(long solutionId, long userId, Long parentId, String content) {
        requirePublishedWithFullAccess(solutionId, userId);
        String clean = StringUtils.trimToEmpty(content);
        if (clean.length() < 2 || clean.length() > 2000) throw new BusinessException(40000, "评论需要 2-2000 个字符");
        if (parentId != null) {
            QuestionSolutionComment parent = commentMapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getSolutionId(), solutionId)) throw new BusinessException(40000, "回复的评论不存在");
        }
        QuestionSolutionComment comment = new QuestionSolutionComment();
        comment.setSolutionId(solutionId); comment.setUserId(userId); comment.setParentId(parentId); comment.setContent(clean); comment.setIsDelete(0);
        commentMapper.insert(comment); solutionMapper.changeCommentCount(solutionId, 1);
        return String.valueOf(comment.getId());
    }

    @Override
    @Transactional
    public boolean deleteComment(long commentId, long userId) {
        QuestionSolutionComment comment = commentMapper.selectById(commentId);
        if (comment == null) throw new BusinessException(ResultCode.NOT_FOUND_ERROR);
        QuestionSolution solution = requirePublishedWithFullAccess(comment.getSolutionId(), userId);
        boolean admin = accessService.decide(userId, solution.getQuestionId()).admin();
        if (!admin && !Objects.equals(comment.getUserId(), userId)) throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        int deleted = commentMapper.deleteById(commentId);
        if (deleted > 0) solutionMapper.changeCommentCount(comment.getSolutionId(), -1);
        return deleted > 0;
    }
}
