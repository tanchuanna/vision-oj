package com.spingbootinit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.model.entity.QuestionSubmit;
import com.spingbootinit.service.impl.SubmissionDashboardServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionDashboardServiceTest {

    @Test
    void detailRejectsOtherUsersSubmissionForSelfScope() {
        QuestionSubmitService submitService = mock(QuestionSubmitService.class);
        QuestionSubmit submit = new QuestionSubmit();
        submit.setId(99L);
        submit.setUserId(200L);
        when(submitService.getById(99L)).thenReturn(submit);
        SubmissionDashboardServiceImpl service = new SubmissionDashboardServiceImpl(
                mock(NamedParameterJdbcTemplate.class), submitService, mock(QuestionService.class),
                mock(UserService.class), new ObjectMapper());

        assertThrows(BusinessException.class, () -> service.getDetail(99L, 100L, false));
    }

    @Test
    void detailCleansJudgeInfoAndNeverReturnsRawSandboxPayload() {
        QuestionSubmitService submitService = mock(QuestionSubmitService.class);
        QuestionService questionService = mock(QuestionService.class);
        QuestionSubmit submit = new QuestionSubmit();
        submit.setId(99L);
        submit.setUserId(100L);
        submit.setQuestionId(8L);
        submit.setLanguage("java");
        submit.setCode("class Main {}\n");
        submit.setStatus(3);
        submit.setCreateTime(new Date());
        submit.setJudgeInfo("{\"message\":\"Compile Error\",\"detail\":\"Main.java:1: error\",\"hiddenCase\":\"secret\",\"sandboxPath\":\"/tmp/private\"}");
        when(submitService.getById(99L)).thenReturn(submit);
        SubmissionDashboardServiceImpl service = new SubmissionDashboardServiceImpl(
                mock(NamedParameterJdbcTemplate.class), submitService, questionService,
                mock(UserService.class), new ObjectMapper());

        var detail = service.getDetail(99L, 100L, false);

        assertEquals("Compile Error", detail.getVerdict());
        assertEquals("Main.java:1: error", detail.getErrorDetail());
        assertFalse(detail.getErrorDetail().contains("secret"));
        assertFalse(detail.getErrorDetail().contains("/tmp/private"));
        assertEquals(14, detail.getCodeLength());
    }
}
