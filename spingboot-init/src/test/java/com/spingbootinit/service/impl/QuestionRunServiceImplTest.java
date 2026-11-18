package com.spingbootinit.service.impl;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.judo.ImageJudgeInputService;
import com.spingbootinit.judo.codesandbox.CodeSandbox;
import com.spingbootinit.judo.codesandbox.CodeSandboxFactory;
import com.spingbootinit.judo.codesandbox.model.ExecuteCodeRequest;
import com.spingbootinit.judo.codesandbox.model.ExecuteCodeResponse;
import com.spingbootinit.model.dto.question.JudgeCase;
import com.spingbootinit.model.dto.question.JudgeConfig;
import com.spingbootinit.model.dto.question.QuestionRunRequest;
import com.spingbootinit.model.entity.Question;
import com.spingbootinit.service.QuestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuestionRunServiceImplTest {

    private final QuestionService questionService = mock(QuestionService.class);
    private final CodeSandboxFactory sandboxFactory = mock(CodeSandboxFactory.class);
    private final ImageJudgeInputService imageJudgeInputService = mock(ImageJudgeInputService.class);
    private final CodeSandbox sandbox = mock(CodeSandbox.class);
    private QuestionRunServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestionRunServiceImpl();
        ReflectionTestUtils.setField(service, "questionService", questionService);
        ReflectionTestUtils.setField(service, "codeSandboxFactory", sandboxFactory);
        ReflectionTestUtils.setField(service, "imageJudgeInputService", imageJudgeInputService);
        ReflectionTestUtils.setField(service, "defaultJudgeStrategy", "example");
        when(sandboxFactory.newInstance("example")).thenReturn(sandbox);
        UserContext.setCurrenUserId(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void requestWithoutModeStillRunsOnlyPublicSamples() {
        Question question = textQuestion(sample("public", "ok", true), sample("hidden", "secret", false));
        when(questionService.getById(1L)).thenReturn(question);
        when(imageJudgeInputService.prepare(eq(question), eq(List.of("public")), eq("python")))
                .thenReturn(List.of());
        when(sandbox.executeCode(any())).thenReturn(success(List.of("ok")));

        QuestionRunRequest request = request(null, "print(input())", null);
        var result = service.runSample(request);

        assertEquals("SAMPLE", result.getMode());
        assertEquals(1, result.getCases().size());
        assertEquals("public", result.getCases().get(0).getInput());
        assertEquals(Boolean.TRUE, result.getAllSamplePassed());
        verify(sandbox).executeCode(any());
    }

    @Test
    void customModePreservesWhitespaceAndHasNoExpectedAnswer() {
        Question question = textQuestion(sample("public", "ok", true));
        when(questionService.getById(1L)).thenReturn(question);
        when(sandbox.executeCode(any())).thenReturn(success(List.of("  result\n")));
        String stdin = "  first line\nsecond line  \n";

        var result = service.runSample(request("CUSTOM", "\nprint('x')\n", stdin));

        ArgumentCaptor<ExecuteCodeRequest> captor = ArgumentCaptor.forClass(ExecuteCodeRequest.class);
        verify(sandbox).executeCode(captor.capture());
        assertEquals(List.of(stdin), captor.getValue().getInputList());
        assertEquals("\nprint('x')\n", captor.getValue().getCode());
        assertEquals("CUSTOM", result.getMode());
        assertNull(result.getAllSamplePassed());
        assertNull(result.getCases().get(0).getExpectedOutput());
        assertNull(result.getCases().get(0).getPassed());
        verifyNoInteractions(imageJudgeInputService);
    }

    @Test
    void customModeAllowsEmptyStdin() {
        when(questionService.getById(1L)).thenReturn(textQuestion(sample("public", "ok", true)));
        when(sandbox.executeCode(any())).thenReturn(success(List.of("hello")));

        var result = service.runSample(request("CUSTOM", "print('hello')", ""));

        assertEquals("", result.getCases().get(0).getInput());
        assertEquals("hello", result.getCases().get(0).getActualOutput());
    }

    @Test
    void customModeRejectsImageQuestion() {
        Question question = textQuestion(sample("/images/a.png", "1", true));
        question.setQuestionType("IMAGE_OBJECT_COUNT");
        when(questionService.getById(1L)).thenReturn(question);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.runSample(request("CUSTOM", "print(1)", "")));

        assertTrue(error.getMessage().contains("图像题"));
        verifyNoInteractions(sandbox);
    }

    @Test
    void sampleModeNeverFallsBackToHiddenCase() {
        when(questionService.getById(1L)).thenReturn(textQuestion(sample("hidden", "secret", false)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.runSample(request("SAMPLE", "print(input())", null)));

        assertTrue(error.getMessage().contains("公开样例"));
        verifyNoInteractions(sandbox, imageJudgeInputService);
    }

    @Test
    void imageSampleUsesSameSemanticConfigurationAsFormalJudge() {
        Question question = textQuestion(sample("http://minio.local/ocr.png", "abcdef", true));
        question.setQuestionType("IMAGE_OCR");
        question.setCountTolerance(0);
        JudgeConfig config = new JudgeConfig();
        config.setOcrEditDistance(2);
        question.setJudgeConfig(config);
        when(questionService.getById(1L)).thenReturn(question);
        when(imageJudgeInputService.prepare(eq(question), anyList(), eq("python"))).thenReturn(List.of("base64"));
        when(sandbox.executeCode(any())).thenReturn(success(List.of("abxxef")));

        var result = service.runSample(request("SAMPLE", "print(input())", null));

        assertEquals(Boolean.TRUE, result.getAllSamplePassed());
    }

    @Test
    void imageCountSampleUsesDedicatedCountTolerance() {
        Question question = textQuestion(sample("http://minio.local/count.png", "{\"person\":2}", true));
        question.setQuestionType("IMAGE_OBJECT_COUNT");
        question.setCountTolerance(0);
        JudgeConfig config = new JudgeConfig();
        config.setCountTolerance(1);
        question.setJudgeConfig(config);
        when(questionService.getById(1L)).thenReturn(question);
        when(imageJudgeInputService.prepare(eq(question), anyList(), eq("python"))).thenReturn(List.of("base64"));
        when(sandbox.executeCode(any())).thenReturn(success(List.of("{\"person\":3}")));

        assertEquals(Boolean.TRUE, service.runSample(request("SAMPLE", "print(input())", null)).getAllSamplePassed());
    }

    private static QuestionRunRequest request(String mode, String code, String input) {
        QuestionRunRequest request = new QuestionRunRequest();
        request.setQuestionId("1");
        request.setLanguage("python");
        request.setCode(code);
        request.setMode(mode);
        request.setCustomInput(input);
        return request;
    }

    private static Question textQuestion(JudgeCase... cases) {
        Question question = new Question();
        question.setId(1L);
        question.setQuestionType("TEXT");
        question.setJudgeCase(List.of(cases));
        return question;
    }

    private static JudgeCase sample(String input, String output, boolean visible) {
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setInput(input);
        judgeCase.setOutput(output);
        judgeCase.setSample(visible);
        return judgeCase;
    }

    private static ExecuteCodeResponse success(List<String> output) {
        return ExecuteCodeResponse.builder().code(0).status(1).outputList(output).build();
    }
}
