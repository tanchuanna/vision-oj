package com.spingbootinit.judo.strategy;

import com.spingbootinit.model.dto.question.JudgeCase;
import com.spingbootinit.model.dto.question.JudgeConfig;
import com.spingbootinit.model.dto.questionsubmit.JudgeInfo;
import com.spingbootinit.model.entity.Question;
import com.spingbootinit.model.enums.JudgeInfoMessageEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectCountJudgeStrategyTest {
    @Test
    void rejectsFractionalAndOverflowCounts() {
        assertFalse(ObjectCountJudgeStrategy.outputsMatch("{\"bus\":1}", "{\"bus\":1.9}", 0));
        assertFalse(ObjectCountJudgeStrategy.outputsMatch("{\"bus\":1}", "{\"bus\":4294967297}", 0));
    }

    @Test
    void acceptsDifferentJsonOrderAndWhitespace() {
        assertTrue(ObjectCountJudgeStrategy.outputsMatch(
                "{\"car\":3,\"person\":2}",
                "{ \"person\" : 2, \"car\" : 3 }",
                0));
    }

    @Test
    void appliesPerClassTolerance() {
        assertTrue(ObjectCountJudgeStrategy.outputsMatch(
                "{\"car\":3,\"person\":2}",
                "{\"car\":4,\"person\":1}",
                1));
        assertFalse(ObjectCountJudgeStrategy.outputsMatch(
                "{\"car\":3,\"person\":2}",
                "{\"car\":5,\"person\":2}",
                1));
    }

    @Test
    void rejectsMalformedOrUnexpectedLabels() {
        assertFalse(ObjectCountJudgeStrategy.outputsMatch("{\"car\":3}", "not-json", 0));
        assertFalse(ObjectCountJudgeStrategy.outputsMatch("{\"car\":3}", "{\"car\":3,\"dog\":1}", 0));
    }

    @Test
    void formalJudgeUsesDedicatedToleranceAndHidesExpectedHiddenCount() {
        Question question = new Question();
        question.setCountTolerance(0);
        JudgeConfig config = new JudgeConfig();
        config.setCountTolerance(1);
        question.setJudgeConfig(config);

        JudgeCase hidden = new JudgeCase();
        hidden.setOutput("{\"person\":2}");
        hidden.setSample(false);
        JudgeContext accepted = context(question, hidden, "{\"person\":3}");
        assertEquals(JudgeInfoMessageEnum.ACCEPTED.getValue(), new ObjectCountJudgeStrategy().doJudge(accepted).getMessage());

        JudgeContext rejected = context(question, hidden, "{\"person\":8}");
        JudgeInfo result = new ObjectCountJudgeStrategy().doJudge(rejected);
        assertEquals(JudgeInfoMessageEnum.WRONG_ANSWER.getValue(), result.getMessage());
        assertTrue(result.getDetail().contains("person"));
        assertFalse(result.getDetail().contains("期望 2"));
        assertFalse(result.getDetail().contains("实际 8"));
    }

    private JudgeContext context(Question question, JudgeCase judgeCase, String output) {
        JudgeContext context = new JudgeContext();
        context.setQuestion(question);
        context.setJudgeCaseList(List.of(judgeCase));
        context.setOutputList(List.of(output));
        JudgeInfo usage = new JudgeInfo();
        usage.setTime(10L);
        usage.setMemory(1024L);
        context.setJudgeInfo(usage);
        return context;
    }
}
