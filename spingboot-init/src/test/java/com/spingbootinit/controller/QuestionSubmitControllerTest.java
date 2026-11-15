package com.spingbootinit.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.model.entity.QuestionSubmit;
import com.spingbootinit.service.QuestionSubmitService;
import com.spingbootinit.service.SubmissionDashboardService;
import com.spingbootinit.service.impl.SubmissionAccessScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QuestionSubmitControllerTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @SuppressWarnings("unchecked")
    @Test
    void mySubmitPageFiltersByCurrentQuestionWhenQuestionIdProvided() {
        QuestionSubmitService service = mock(QuestionSubmitService.class);
        LambdaQueryChainWrapper<QuestionSubmit> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(service.lambdaQuery()).thenReturn(query);
        when(query.eq(any(SFunction.class), any())).thenReturn(query);
        when(query.orderByAsc(any(SFunction.class))).thenReturn(query);
        when(query.page(any(Page.class))).thenReturn(new Page<>(1, 10, 0));

        QuestionSubmitController controller = new QuestionSubmitController();
        ReflectionTestUtils.setField(controller, "questionSubmitService", service);
        UserContext.setCurrenUserId(7L);

        controller.pageMyAllQuestionSubmits(null, 99L, 1, 10);

        verify(query).eq(any(), eq(7L));
        verify(query).eq(any(), eq(99L));
    }

    @Test
    void mySubmitPageRejectsNonPositiveQuestionId() {
        QuestionSubmitController controller = new QuestionSubmitController();
        UserContext.setCurrenUserId(7L);

        assertThrows(BusinessException.class,
                () -> controller.pageMyAllQuestionSubmits(null, 0L, 1, 10));
    }

    @Test
    void publicStatisticsRejectsNonPositiveQuestionId() {
        QuestionSubmitController controller = new QuestionSubmitController();
        assertThrows(BusinessException.class, () -> controller.getPublicQuestionStatistics(0L));
    }

    @Test
    void publicStatisticsAllowsGuestAndDoesNotRequireLogin() {
        QuestionSubmitService service = mock(QuestionSubmitService.class);
        QuestionSubmitController controller = new QuestionSubmitController();
        ReflectionTestUtils.setField(controller, "questionSubmitService", service);

        controller.getPublicQuestionStatistics(99L);

        verify(service).getQuestionStatistics(99L, null);
    }

    @Test
    void teacherCannotFilterSubmissionDashboardByUnassignedStudent() {
        SubmissionAccessScopeService accessScope = mock(SubmissionAccessScopeService.class);
        SubmissionDashboardService dashboard = mock(SubmissionDashboardService.class);
        when(accessScope.resolve(7L, "MANAGED"))
                .thenReturn(SubmissionAccessScopeService.Access.managedStudents(List.of(21L)));

        QuestionSubmitController controller = new QuestionSubmitController();
        ReflectionTestUtils.setField(controller, "submissionAccessScopeService", accessScope);
        ReflectionTestUtils.setField(controller, "submissionDashboardService", dashboard);
        UserContext.setCurrenUserId(7L);

        assertThrows(BusinessException.class, () -> controller.pageSubmissions(
                "MANAGED", 22L, null, null, null, null,
                null, null, "DESC", 1, 15));
        verifyNoInteractions(dashboard);
    }
}
