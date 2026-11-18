package com.spingbootinit.service;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.judo.ImageJudgeInputService;
import com.spingbootinit.judo.codesandbox.CodeSandboxFactory;
import com.spingbootinit.judo.codesandbox.CodeSandboxProxy;
import com.spingbootinit.judo.codesandbox.model.ExecuteCodeRequest;
import com.spingbootinit.judo.codesandbox.model.ExecuteCodeResponse;
import com.spingbootinit.judo.strategy.ImageJudgeConfigPolicy;
import com.spingbootinit.judo.strategy.ImageOutputJudgeStrategy;
import com.spingbootinit.judo.strategy.ObjectCountJudgeStrategy;
import com.spingbootinit.model.dto.question.JudgeCase;
import com.spingbootinit.model.dto.question.QuestionAddRequest;
import com.spingbootinit.model.entity.Question;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 教师保存前的验题：检查数据，并在正式沙箱运行各类图像题参考代码。 */
@Service
@RequiredArgsConstructor
public class QuestionPreflightService {
    private static final Pattern PYTHON_BLOCK = Pattern.compile("```python\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private final QuestionService questionService;
    private final ImageJudgeInputService imageJudgeInputService;
    private final CodeSandboxFactory codeSandboxFactory;

    @Value("${codesandbox.type:remote}")
    private String sandboxType;

    public record Check(String name, boolean passed, String detail) {}
    public record PreflightResult(boolean ready, List<Check> checks) {}

    public PreflightResult check(QuestionAddRequest request) {
        List<Check> checks = new ArrayList<>();
        Question question = new Question();
        BeanUtils.copyProperties(request, question);
        try {
            questionService.validQuestion(question, false);
            checks.add(new Check("题目与用例格式", true, "题面、样例、隐藏用例和判题配置完整"));
        } catch (BusinessException e) {
            checks.add(new Check("题目与用例格式", false, e.getMessage()));
            return new PreflightResult(false, checks);
        }
        if (!question.getQuestionType().startsWith("IMAGE_")) {
            checks.add(new Check("参考程序", true, "传统文本题完成结构检查"));
            return new PreflightResult(true, checks);
        }
        List<JudgeCase> cases = question.getJudgeCase();
        int legacyTolerance = question.getCountTolerance() == null ? 0 : question.getCountTolerance();
        boolean diverse = hasDifferentExpectedAnswer(question.getQuestionType(), cases,
                legacyTolerance, question.getJudgeConfig());
        checks.add(new Check("多图答案区分度", diverse, diverse
                ? "公开与隐藏图片的标准输出具有区分度，未发现直接固定输出可同时通过"
                : "多张图片可被同一固定输出同时通过；请更换图片或复核标准答案与容差"));
        String code = pythonCode(question.getAnswer());
        if (code == null) {
            checks.add(new Check("参考程序", false, "请在参考答案中提供 ```python 代码块，供发布前实际运行"));
            return new PreflightResult(false, checks);
        }
        try {
            List<String> inputs = cases.stream().map(JudgeCase::getInput).toList();
            List<String> images = imageJudgeInputService.prepare(question, inputs, "python");
            ExecuteCodeResponse response = new CodeSandboxProxy(codeSandboxFactory.newInstance(sandboxType))
                    .executeCode(ExecuteCodeRequest.builder().code(code).language("python")
                            .userId(UserContext.getCurrentUserId())
                            .inputList(inputs).imageBase64List(images).build());
            List<String> outputs = response.getOutputList();
            if ((response.getCode() != null && response.getCode() != 0) || outputs == null || outputs.size() != cases.size()) {
                String reason = response.getMessage() == null ? "沙箱未返回完整结果" : response.getMessage();
                checks.add(new Check("参考程序实跑", false, "沙箱运行失败：" + reason));
            } else {
                int mismatch = -1;
                for (int i = 0; i < cases.size(); i++) {
                    if (!matchesReference(question.getQuestionType(), cases.get(i).getOutput(), outputs.get(i),
                            question.getCountTolerance() == null ? 0 : question.getCountTolerance(), question.getJudgeConfig())) {
                        mismatch = i + 1;
                        break;
                    }
                }
                checks.add(new Check("参考程序实跑", mismatch < 0,
                        mismatch < 0 ? "参考程序已在判题沙箱通过全部 " + cases.size() + " 组用例"
                                : "参考程序第 " + mismatch + " 组输出与标准答案不一致，请复核图片与标注"));
            }
        } catch (Exception e) {
            checks.add(new Check("参考程序实跑", false, "判题沙箱不可用或参考程序执行失败：" + safeReason(e)));
        }
        return new PreflightResult(checks.stream().allMatch(Check::passed), checks);
    }

    static boolean matchesReference(String type, String expected, String actual, int tolerance) {
        return matchesReference(type, expected, actual, tolerance, null);
    }

    static boolean matchesReference(String type, String expected, String actual, int tolerance, com.spingbootinit.model.dto.question.JudgeConfig config) {
        return "IMAGE_OBJECT_COUNT".equals(type)
                ? ObjectCountJudgeStrategy.outputsMatch(expected, actual,
                ImageJudgeConfigPolicy.countTolerance(config, tolerance))
                : ImageOutputJudgeStrategy.outputsMatch(type, expected, actual, tolerance, config);
    }

    static boolean hasDifferentExpectedAnswer(String type, List<JudgeCase> cases, int tolerance,
                                              com.spingbootinit.model.dto.question.JudgeConfig config) {
        if (cases == null || cases.size() < 2
                || cases.stream().noneMatch(c -> Boolean.TRUE.equals(c.getSample()))
                || cases.stream().noneMatch(c -> !Boolean.TRUE.equals(c.getSample()))) return false;
        if ("IMAGE_OBJECT_COUNT".equals(type)) {
            return hasDifferentCountAnswer(cases, ImageJudgeConfigPolicy.countTolerance(config, tolerance));
        }
        List<String> expected = cases.stream().map(JudgeCase::getOutput).toList();
        for (String fixedOutput : expected) {
            boolean passesEveryImage = expected.stream().allMatch(answer ->
                    matchesReference(type, answer, fixedOutput, tolerance, config));
            if (passesEveryImage) return false;
        }
        return true;
    }

    static boolean hasDifferentCountAnswer(List<JudgeCase> cases) {
        return hasDifferentCountAnswer(cases, 0);
    }

    static boolean hasDifferentCountAnswer(List<JudgeCase> cases, int tolerance) {
        if (cases == null || cases.size() < 2) return false;
        if (cases.stream().noneMatch(c -> Boolean.TRUE.equals(c.getSample()))
                || cases.stream().noneMatch(c -> !Boolean.TRUE.equals(c.getSample()))) return false;
        return ObjectCountJudgeStrategy.hasNoSharedCountOutput(
                cases.stream().map(JudgeCase::getOutput).toList(), tolerance);
    }

    static String pythonCode(String answer) {
        if (answer == null) return null;
        Matcher matcher = PYTHON_BLOCK.matcher(answer);
        if (!matcher.find()) return null;
        String code = matcher.group(1).trim();
        return code.isBlank() ? null : code;
    }

    private static String safeReason(Exception e) {
        if (e instanceof BusinessException && e.getMessage() != null) return e.getMessage();
        return e.getClass().getSimpleName();
    }
}
