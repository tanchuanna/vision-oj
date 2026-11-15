package com.spingbootinit.service.impl;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.model.dto.question.solution.SolutionSaveRequest;
import com.spingbootinit.model.entity.QuestionSolution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestionSolutionServiceTest {

    @Test
    void hintProjectionNeverContainsFullContentOrReviewReason() {
        QuestionSolution solution = publishedSolution();
        solution.setReviewReason("internal review note");

        var detail = QuestionSolutionServiceImpl.toDetail(solution, false, false, null);

        assertEquals("HINT", detail.getAccessLevel());
        assertNull(detail.getContent());
        assertNull(detail.getReviewReason());
        assertEquals("先分析输入范围，再选择线性扫描。", detail.getHintContent());
    }

    @Test
    void acceptedProjectionContainsFullContent() {
        QuestionSolution solution = publishedSolution();

        var detail = QuestionSolutionServiceImpl.toDetail(solution, true, false, null);

        assertEquals("FULL", detail.getAccessLevel());
        assertEquals("完整算法与代码", detail.getContent());
    }

    @Test
    void editingPublishedSolutionAlwaysReturnsToPendingReview() {
        QuestionSolution existing = publishedSolution();
        SolutionSaveRequest request = saveRequest();

        QuestionSolutionServiceImpl.applySave(existing, request, false);

        assertEquals(QuestionSolution.PENDING, existing.getReviewStatus());
        assertNull(existing.getReviewedBy());
        assertNull(existing.getReviewedAt());
        assertEquals(0, existing.getIsOfficial());
    }

    @Test
    void nonAcceptedUserCannotSave() {
        assertThrows(BusinessException.class,
                () -> QuestionSolutionServiceImpl.requireWrite(
                        new com.spingbootinit.service.QuestionSolutionAccessService.AccessDecision(false, false, false, false)));
    }

    private static QuestionSolution publishedSolution() {
        QuestionSolution solution = new QuestionSolution();
        solution.setId(1L);
        solution.setQuestionId(11L);
        solution.setUserId(7L);
        solution.setTitle("双指针解法");
        solution.setSummary("从左右两端收缩区间");
        solution.setHintContent("先分析输入范围，再选择线性扫描。");
        solution.setContent("完整算法与代码");
        solution.setReviewStatus(QuestionSolution.PUBLISHED);
        solution.setIsOfficial(1);
        solution.setViewCount(3);
        solution.setLikeCount(2);
        solution.setCommentCount(1);
        return solution;
    }

    private static SolutionSaveRequest saveRequest() {
        SolutionSaveRequest request = new SolutionSaveRequest();
        request.setId("1");
        request.setQuestionId("11");
        request.setTitle("更新后的标题");
        request.setSummary("更新后的摘要");
        request.setHintContent("安全提示内容不少于十个字符");
        request.setContent("更新后的完整题解正文内容，包含算法解释、复杂度分析和参考代码。");
        return request;
    }
}
