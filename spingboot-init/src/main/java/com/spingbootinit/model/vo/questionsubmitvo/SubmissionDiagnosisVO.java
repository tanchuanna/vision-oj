package com.spingbootinit.model.vo.questionsubmitvo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 判题结果 AI 诊断：分层提示，不直接给出可提交的完整答案。
 */
@Data
public class SubmissionDiagnosisVO {
    /** 归一化后的评测结论，例如 Wrong Answer */
    private String verdict;
    /** 一句话结论 */
    private String headline;
    /** 错因定位 */
    private String rootCause;
    /** 依据：具体代码位置、公开样例或评测信息 */
    private List<String> evidence = new ArrayList<>();
    /** 三层递进提示：方向 → 关键步骤 → 可落地改法 */
    private List<String> hints = new ArrayList<>();
    /** 下一步具体动作 */
    private String nextStep;
    /** 可选关键代码片段（不会给出完整可提交程序） */
    private String snippet;
}
