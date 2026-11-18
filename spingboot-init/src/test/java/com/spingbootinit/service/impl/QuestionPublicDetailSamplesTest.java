package com.spingbootinit.service.impl;

import com.spingbootinit.model.dto.question.JudgeCase;
import com.spingbootinit.model.entity.Question;
import com.spingbootinit.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class QuestionPublicDetailSamplesTest {
    @Test
    void unmarkedCasesAreNeverExposedAsPublicSamples() {
        QuestionServiceImpl service = spy(new QuestionServiceImpl());
        ReflectionTestUtils.setField(service, "userService", mock(UserService.class));
        Question question = new Question();
        question.setUserId(7L);
        question.setQuestionType("TEXT");
        JudgeCase hidden = new JudgeCase();
        hidden.setInput("secret input");
        hidden.setOutput("secret answer");
        hidden.setSample(false);
        question.setJudgeCase(List.of(hidden));
        doReturn(question).when(service).getById(1L);

        assertTrue(service.getByIdPublicDetail(1L).getSampleJudgeCase().isEmpty());
    }

    @Test
    void onlyMarkedCasesAreReturned() {
        QuestionServiceImpl service = spy(new QuestionServiceImpl());
        ReflectionTestUtils.setField(service, "userService", mock(UserService.class));
        Question question = new Question();
        question.setUserId(7L);
        question.setQuestionType("IMAGE_OBJECT_COUNT");
        JudgeCase publicCase = new JudgeCase();
        publicCase.setInput("public image");
        publicCase.setOutput("1");
        publicCase.setSample(true);
        JudgeCase hidden = new JudgeCase();
        hidden.setInput("hidden image");
        hidden.setOutput("2");
        hidden.setSample(false);
        question.setJudgeCase(List.of(publicCase, hidden));
        doReturn(question).when(service).getById(1L);

        assertEquals(List.of(publicCase), service.getByIdPublicDetail(1L).getSampleJudgeCase());
    }
}
