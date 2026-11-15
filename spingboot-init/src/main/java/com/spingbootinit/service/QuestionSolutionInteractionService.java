package com.spingbootinit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spingbootinit.model.vo.question.solution.SolutionCommentVO;
import com.spingbootinit.model.vo.question.solution.SolutionLikeVO;

public interface QuestionSolutionInteractionService {
    SolutionLikeVO toggleLike(long solutionId, long userId);
    Page<SolutionCommentVO> pageComments(long solutionId, long userId, long current, long size);
    String addComment(long solutionId, long userId, Long parentId, String content);
    boolean deleteComment(long commentId, long userId);
}
