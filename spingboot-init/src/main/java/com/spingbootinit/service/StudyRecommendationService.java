package com.spingbootinit.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spingbootinit.model.entity.Question;
import com.spingbootinit.model.entity.QuestionSubmit;
import com.spingbootinit.model.vo.questionvo.StudyRecommendationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 根据本人真实提交和公开题目标签推荐练习，不读取代码或隐藏用例。 */
@Service
@RequiredArgsConstructor
public class StudyRecommendationService {
    private static final int CANDIDATE_LIMIT = 300;
    private static final int HISTORY_LIMIT = 200;
    private static final int RECOMMEND_LIMIT = 6;
    private static final int RECOMMEND_LIMIT_MAX = 10;
    private static final int WEAK_TAG_LIMIT = 4;
    private static final int DAILY_GOAL = 3;

    private final QuestionService questionService;
    private final QuestionSubmitService questionSubmitService;

    public StudyRecommendationVO recommend(long userId) {
        List<StudyRecommendationEngine.Candidate> candidates = loadCandidates();
        List<StudyRecommendationEngine.Attempt> attempts = loadAttempts(userId);

        StudyRecommendationVO vo = new StudyRecommendationVO();
        List<StudyRecommendationEngine.TagMastery> weakTags =
                StudyRecommendationEngine.tagMastery(candidates, attempts);
        vo.setWeakTags(weakTags.stream()
                .filter(t -> t.mastery() < 80)
                .limit(WEAK_TAG_LIMIT)
                .toList());
        vo.setRecommendations(StudyRecommendationEngine.rank(candidates, attempts, RECOMMEND_LIMIT));
        vo.setTodaySolved(countTodaySolved(userId));
        vo.setDailyGoal(DAILY_GOAL);
        return vo;
    }

    /**
     * 指定题目数量的推荐，供老师一键布置练习复用同一套排序规则。
     */
    public List<StudyRecommendationEngine.Recommendation> recommendFor(long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(RECOMMEND_LIMIT_MAX, limit));
        return StudyRecommendationEngine.rank(loadCandidates(), loadAttempts(userId), safeLimit);
    }

    private List<StudyRecommendationEngine.Candidate> loadCandidates() {
        List<Question> questions = questionService.list(new LambdaQueryWrapper<Question>()
                .select(Question::getId, Question::getTitle, Question::getQuestionType, Question::getTags)
                .orderByDesc(Question::getCreateTime).last("LIMIT " + CANDIDATE_LIMIT));
        return questions.stream()
                .map(q -> new StudyRecommendationEngine.Candidate(q.getId(), q.getTitle(), q.getQuestionType(), q.getTags()))
                .toList();
    }

    private List<StudyRecommendationEngine.Attempt> loadAttempts(long userId) {
        List<QuestionSubmit> submissions = questionSubmitService.list(new LambdaQueryWrapper<QuestionSubmit>()
                .select(QuestionSubmit::getQuestionId, QuestionSubmit::getJudgeInfo)
                .eq(QuestionSubmit::getUserId, userId)
                .orderByDesc(QuestionSubmit::getCreateTime).last("LIMIT " + HISTORY_LIMIT));
        return submissions.stream()
                .map(s -> new StudyRecommendationEngine.Attempt(s.getQuestionId(), verdict(s.getJudgeInfo())))
                .toList();
    }

    private int countTodaySolved(long userId) {
        List<QuestionSubmit> today = questionSubmitService.list(new LambdaQueryWrapper<QuestionSubmit>()
                .select(QuestionSubmit::getQuestionId, QuestionSubmit::getJudgeInfo)
                .eq(QuestionSubmit::getUserId, userId)
                .ge(QuestionSubmit::getCreateTime, LocalDate.now().atStartOfDay())
                .last("LIMIT 500"));
        Set<Long> solved = new HashSet<>();
        for (QuestionSubmit submit : today) {
            if ("Accepted".equalsIgnoreCase(verdict(submit.getJudgeInfo()))) {
                solved.add(submit.getQuestionId());
            }
        }
        return solved.size();
    }

    private String verdict(String judgeInfo) {
        try {
            return JSONUtil.parseObj(judgeInfo).getStr("message");
        } catch (Exception ignored) {
            return null;
        }
    }
}
