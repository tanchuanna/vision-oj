package com.spingbootinit.service;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.*;

/** 只用真实提交记录和题目标签排序；每条推荐都能解释其依据。 */
public final class StudyRecommendationEngine {
    private static final Set<String> DIFFICULTY = Set.of("入门", "简单", "中等", "困难");

    private StudyRecommendationEngine() {}

    public record Candidate(long questionId, String title, String questionType, List<String> tags) {}

    public record Attempt(long questionId, String verdict) {}

    public record Recommendation(@JsonSerialize(using = ToStringSerializer.class) long questionId,
                                 String title, String questionType, String difficulty,
                                 String focusTag, String reason) {}

    /** 知识点掌握度：按“去重后的题目”计算，避免同一题多次提交放大权重。 */
    public record TagMastery(String tag, int attempted, int solved, int mastery, String level) {}

    /**
     * 汇总知识点掌握度，只返回有提交记录的知识点，掌握度低的排前面。
     */
    public static List<TagMastery> tagMastery(List<Candidate> questions, List<Attempt> attempts) {
        if (questions == null || questions.isEmpty() || attempts == null) return List.of();
        Map<Long, Candidate> byId = new HashMap<>();
        for (Candidate q : questions) if (q != null) byId.put(q.questionId(), q);

        Map<String, Set<Long>> attempted = new LinkedHashMap<>();
        Map<String, Set<Long>> solved = new LinkedHashMap<>();
        for (Attempt attempt : attempts) {
            if (attempt == null) continue;
            Candidate question = byId.get(attempt.questionId());
            if (question == null) continue;
            for (String tag : knowledgeTags(question.tags())) {
                attempted.computeIfAbsent(tag, t -> new HashSet<>()).add(question.questionId());
                if ("Accepted".equalsIgnoreCase(attempt.verdict())) {
                    solved.computeIfAbsent(tag, t -> new HashSet<>()).add(question.questionId());
                }
            }
        }

        List<TagMastery> result = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> entry : attempted.entrySet()) {
            int tried = entry.getValue().size();
            int passed = solved.getOrDefault(entry.getKey(), Set.of()).size();
            int mastery = tried == 0 ? 0 : (int) Math.round(100.0 * passed / tried);
            result.add(new TagMastery(entry.getKey(), tried, passed, mastery, masteryLevel(mastery)));
        }
        result.sort(Comparator.comparingInt(TagMastery::mastery)
                .thenComparing(Comparator.comparingInt(TagMastery::attempted).reversed())
                .thenComparing(TagMastery::tag));
        return result;
    }

    public static List<Recommendation> rank(List<Candidate> questions, List<Attempt> attempts, int limit) {
        if (questions == null || questions.isEmpty() || limit <= 0) return List.of();
        Map<Long, Candidate> byId = new HashMap<>();
        for (Candidate q : questions) if (q != null) byId.put(q.questionId(), q);

        Set<Long> solved = new HashSet<>();
        Set<Long> tried = new HashSet<>();
        Map<String, Integer> weakTags = new HashMap<>();
        Set<String> failedSignatures = new HashSet<>();
        if (attempts != null) {
            for (Attempt attempt : attempts) {
                if (attempt == null) continue;
                tried.add(attempt.questionId());
                if ("Accepted".equalsIgnoreCase(attempt.verdict())) solved.add(attempt.questionId());
            }
            for (Attempt attempt : attempts) {
                if (attempt == null || solved.contains(attempt.questionId())
                        || "Accepted".equalsIgnoreCase(attempt.verdict()) || attempt.verdict() == null
                        || Set.of("Waiting", "Judging", "System Error").contains(attempt.verdict())) continue;
                Candidate question = byId.get(attempt.questionId());
                if (question == null) continue;
                for (String tag : knowledgeTags(question.tags())) {
                    // 同一道题同一知识点只记一次失败，避免重复提交把权重刷高
                    if (failedSignatures.add(tag + "#" + question.questionId())) {
                        weakTags.merge(tag, 1, Integer::sum);
                    }
                }
            }
        }

        String targetDifficulty = targetDifficulty(solved, byId);

        record Scored(Recommendation recommendation, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (Candidate question : questions) {
            if (question == null || solved.contains(question.questionId())) continue;
            List<String> tags = knowledgeTags(question.tags());
            String focus = tags.stream()
                    .max(Comparator.comparingInt((String t) -> weakTags.getOrDefault(t, 0))
                            .thenComparing(Comparator.reverseOrder()))
                    .orElse("");
            int weakness = weakTags.getOrDefault(focus, 0);
            String difficulty = difficultyOf(question.tags());
            int score = Math.min(weakness, 4) * 25;
            if (!tried.contains(question.questionId())) score += 8;
            if (difficulty != null && difficulty.equals(targetDifficulty)) score += 6;
            if ("入门".equals(difficulty) || "简单".equals(difficulty)) score += 3;
            String reason = weakness > 0
                    ? "你最近在「" + focus + "」相关题目还没通过，推荐继续练这个知识点"
                    : tried.contains(question.questionId())
                    ? "你尝试过这道题但还没通过，适合回顾后再挑战"
                    : difficulty != null
                    ? "按你当前通过难度，推荐从「" + difficulty + "」继续推进"
                    : "根据当前可练题目推荐；完成提交后会结合你的薄弱知识点调整";
            scored.add(new Scored(new Recommendation(question.questionId(), question.title(),
                    question.questionType(), difficulty, focus, reason), score));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparingLong(s -> s.recommendation().questionId()));

        // 知识点去重：同一 focusTag 最多 2 道，让推荐覆盖更多薄弱点
        Map<String, Integer> pickedByTag = new HashMap<>();
        List<Recommendation> picked = new ArrayList<>();
        for (Scored item : scored) {
            if (picked.size() >= limit) break;
            String tag = item.recommendation().focusTag();
            if (tag != null && !tag.isBlank() && pickedByTag.getOrDefault(tag, 0) >= 2) continue;
            picked.add(item.recommendation());
            if (tag != null && !tag.isBlank()) pickedByTag.merge(tag, 1, Integer::sum);
        }
        return picked;
    }

    private static String targetDifficulty(Set<Long> solved, Map<Long, Candidate> byId) {
        Map<String, Integer> counts = new HashMap<>();
        for (Long id : solved) {
            Candidate question = byId.get(id);
            if (question == null) continue;
            String difficulty = difficultyOf(question.tags());
            if (difficulty != null) counts.merge(difficulty, 1, Integer::sum);
        }
        String highest = null;
        for (String level : List.of("入门", "简单", "中等", "困难")) {
            if (counts.getOrDefault(level, 0) > 0) highest = level;
        }
        if (highest == null) return "简单";
        return switch (highest) {
            case "入门" -> "简单";
            case "简单" -> "中等";
            default -> highest;
        };
    }

    private static String masteryLevel(int mastery) {
        if (mastery >= 80) return "已掌握";
        if (mastery >= 40) return "巩固中";
        return "待加强";
    }

    private static String difficultyOf(List<String> tags) {
        if (tags == null) return null;
        for (String tag : tags) {
            if (tag != null && DIFFICULTY.contains(tag)) return tag;
        }
        return null;
    }

    private static List<String> knowledgeTags(List<String> tags) {
        if (tags == null) return List.of();
        return tags.stream().filter(t -> t != null && !t.isBlank() && !DIFFICULTY.contains(t)).distinct().toList();
    }
}
