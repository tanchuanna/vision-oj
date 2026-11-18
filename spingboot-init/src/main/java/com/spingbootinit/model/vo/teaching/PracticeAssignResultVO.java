package com.spingbootinit.model.vo.teaching;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一键布置的结果摘要。
 */
@Data
public class PracticeAssignResultVO {
    private String title;
    /** 成功生成练习的学生数 */
    private int created;
    /** 被跳过的学生数（无题库推荐或无权限） */
    private int skipped;
    /** 写入的题目总数 */
    private int totalQuestions;
    private List<String> skippedStudentNames = new ArrayList<>();
}
