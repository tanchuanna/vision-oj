package com.spingbootinit.model.vo.questionvo;

import com.spingbootinit.service.StudyRecommendationEngine;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 个性化练习推荐：知识点掌握度 + 今日训练 + 推荐题目。
 */
@Data
public class StudyRecommendationVO {
    /** 掌握度最低的知识点（最多 4 个） */
    private List<StudyRecommendationEngine.TagMastery> weakTags = new ArrayList<>();
    /** 推荐练习题目 */
    private List<StudyRecommendationEngine.Recommendation> recommendations = new ArrayList<>();
    /** 今天已通过（AC）的题目数 */
    private int todaySolved;
    /** 每日训练目标题数 */
    private int dailyGoal;
}
