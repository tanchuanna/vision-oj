package com.spingbootinit.service.impl;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.mapper.QuestionSolutionCommentMapper;
import com.spingbootinit.mapper.QuestionSolutionLikeMapper;
import com.spingbootinit.mapper.QuestionSolutionMapper;
import com.spingbootinit.model.entity.QuestionSolution;
import com.spingbootinit.service.QuestionSolutionAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class QuestionSolutionInteractionServiceTest {
    private final QuestionSolutionMapper solutionMapper = mock(QuestionSolutionMapper.class);
    private final QuestionSolutionLikeMapper likeMapper = mock(QuestionSolutionLikeMapper.class);
    private final QuestionSolutionCommentMapper commentMapper = mock(QuestionSolutionCommentMapper.class);
    private final QuestionSolutionAccessService accessService = mock(QuestionSolutionAccessService.class);
    private QuestionSolutionInteractionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestionSolutionInteractionServiceImpl();
        ReflectionTestUtils.setField(service, "solutionMapper", solutionMapper);
        ReflectionTestUtils.setField(service, "likeMapper", likeMapper);
        ReflectionTestUtils.setField(service, "commentMapper", commentMapper);
        ReflectionTestUtils.setField(service, "accessService", accessService);
        QuestionSolution solution = new QuestionSolution();
        solution.setId(1L);
        solution.setQuestionId(11L);
        solution.setReviewStatus(QuestionSolution.PUBLISHED);
        when(solutionMapper.selectById(1L)).thenReturn(solution);
    }

    @Test
    void nonAcceptedUserCannotLikeOrReadComments() {
        when(accessService.decide(7L, 11L)).thenReturn(
                new QuestionSolutionAccessService.AccessDecision(false, false, false, false));

        assertThrows(BusinessException.class, () -> service.toggleLike(1L, 7L));
        assertThrows(BusinessException.class, () -> service.pageComments(1L, 7L, 1, 10));
        verifyNoInteractions(likeMapper, commentMapper);
    }

    @Test
    void invalidCommentIsRejectedBeforeInsert() {
        when(accessService.decide(7L, 11L)).thenReturn(
                new QuestionSolutionAccessService.AccessDecision(true, true, true, false));

        assertThrows(BusinessException.class, () -> service.addComment(1L, 7L, null, " "));
        verify(commentMapper, never()).insert(any(com.spingbootinit.model.entity.QuestionSolutionComment.class));
    }
}
