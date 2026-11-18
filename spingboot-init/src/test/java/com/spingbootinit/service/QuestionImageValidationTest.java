package com.spingbootinit.service;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.model.dto.question.JudgeCase;
import com.spingbootinit.model.dto.question.JudgeConfig;
import com.spingbootinit.model.entity.Question;
import com.spingbootinit.service.impl.QuestionServiceImpl;
import com.spingbootinit.utils.MinioUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionImageValidationTest {
    private QuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestionServiceImpl();
        MinioUtils minio = mock(MinioUtils.class);
        when(minio.isTrustedObjectUrl(anyString())).thenReturn(true);
        ReflectionTestUtils.setField(service, "minioUtils", minio);
    }

    @Test
    void normalizesDedicatedConfigurationAndKeepsLegacyCompatibilityValue() {
        Question count = question("IMAGE_OBJECT_COUNT", "YOLO_GENERAL", "{\"person\":2}", "{\"person\":4}");
        count.setCountTolerance(1);
        count.getJudgeConfig().setCountTolerance(2);
        service.validQuestion(count, false);
        assertEquals(2, count.getCountTolerance());
        assertEquals(2, count.getJudgeConfig().getCountTolerance());

        Question detection = question("IMAGE_OBJECT_DETECTION", "YOLO_GENERAL",
                "[{\"label\":\"car\",\"x1\":0.1,\"y1\":0.1,\"x2\":0.5,\"y2\":0.5}]",
                "[{\"label\":\"bus\",\"x1\":0.2,\"y1\":0.2,\"x2\":0.7,\"y2\":0.7}]");
        detection.getJudgeConfig().setIouThreshold(0.65);
        service.validQuestion(detection, false);
        assertEquals(65, detection.getCountTolerance());
    }

    @Test
    void rejectsWrongProtocolAndUnconfirmedCandidate() {
        Question classification = question("IMAGE_CLASSIFICATION", "YOLO_GENERAL", "car\nbus", "car");
        assertThrows(BusinessException.class, () -> service.validQuestion(classification, false));

        Question detection = question("IMAGE_OBJECT_DETECTION", "YOLO_GENERAL",
                "[{\"label\":\"car\",\"x1\":-0.1,\"y1\":0.1,\"x2\":0.5,\"y2\":0.5}]",
                "[{\"label\":\"bus\",\"x1\":0.2,\"y1\":0.2,\"x2\":0.7,\"y2\":0.7}]");
        assertThrows(BusinessException.class, () -> service.validQuestion(detection, false));

        Question analysis = question("IMAGE_ANALYSIS", "OPENCV_ANALYSIS", "{\"width\":100}", "{\"width\":101}");
        analysis.getJudgeConfig().setAnalysisVersion(2);
        assertThrows(BusinessException.class, () -> service.validQuestion(analysis, false));

        Question unconfirmed = question("IMAGE_OCR", "EASYOCR_ZH_EN", "hello", "world");
        unconfirmed.setQualityReview(Map.of("candidateConfirmed", false));
        assertThrows(BusinessException.class, () -> service.validQuestion(unconfirmed, false));
    }

    @Test
    void rejectsSemanticConfigurationOutsideAllowedRange() {
        Question detection = question("IMAGE_OBJECT_DETECTION", "YOLO_GENERAL",
                "[{\"label\":\"car\",\"x1\":0.1,\"y1\":0.1,\"x2\":0.5,\"y2\":0.5}]",
                "[{\"label\":\"bus\",\"x1\":0.2,\"y1\":0.2,\"x2\":0.7,\"y2\":0.7}]");
        detection.getJudgeConfig().setIouThreshold(0.99);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.validQuestion(detection, false));
        assertTrue(ex.getMessage().contains("IoU"));

        Question count = question("IMAGE_OBJECT_COUNT", "YOLO_GENERAL", "{\"person\":2}", "{\"person\":4}");
        count.getJudgeConfig().setCountTolerance(-1);
        assertThrows(BusinessException.class, () -> service.validQuestion(count, false));

        Question ocr = question("IMAGE_OCR", "EASYOCR_ZH_EN", "hello", "world");
        ocr.getJudgeConfig().setOcrEditDistance(101);
        assertThrows(BusinessException.class, () -> service.validQuestion(ocr, false));

        Question analysis = question("IMAGE_ANALYSIS", "OPENCV_ANALYSIS",
                "{\"width\":100,\"height\":80,\"mode\":\"RGB\",\"mean_gray\":100.0,\"edge_strength\":5.0}",
                "{\"width\":120,\"height\":90,\"mode\":\"RGB\",\"mean_gray\":101.0,\"edge_strength\":6.0}");
        analysis.getJudgeConfig().setAnalysisVersion(2);
        analysis.getJudgeConfig().setMeanGrayTolerance(-0.1);
        assertThrows(BusinessException.class, () -> service.validQuestion(analysis, false));
    }

    private Question question(String type, String model, String publicOutput, String hiddenOutput) {
        Question question = new Question();
        question.setTitle("图像协议校验题");
        question.setContent("这是一道用于验证五类图像编程题输出协议和发布规则的测试题目。");
        question.setAnswer("参考答案包含完整思路与 ```python\nprint('ok')\n``` 示例代码。");
        question.setQuestionType(type);
        question.setVisionModelKey(model);
        question.setImageUrl("http://minio.local/public.png");
        question.setGenerationSource("UPLOAD");
        question.setCountTolerance(0);
        question.setQualityReview(Map.of("candidateConfirmed", true));
        question.setJudgeCase(List.of(
                judgeCase("http://minio.local/public.png", publicOutput, true),
                judgeCase("http://minio.local/hidden.png", hiddenOutput, false)));
        JudgeConfig config = new JudgeConfig();
        config.setTimeLimit(10_000L);
        config.setMemoryLimit(262_144L);
        config.setStackLimit(65_536L);
        question.setJudgeConfig(config);
        return question;
    }

    private JudgeCase judgeCase(String input, String output, boolean sample) {
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setInput(input);
        judgeCase.setOutput(output);
        judgeCase.setSample(sample);
        return judgeCase;
    }
}
