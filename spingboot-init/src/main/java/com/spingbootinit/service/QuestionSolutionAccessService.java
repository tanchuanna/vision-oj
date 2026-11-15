package com.spingbootinit.service;

import com.spingbootinit.model.vo.question.solution.SolutionEligibilityVO;

public interface QuestionSolutionAccessService {
    boolean hasAccepted(long userId, long questionId);

    AccessDecision decide(Long userId, long questionId);

    default SolutionEligibilityVO eligibility(Long userId, long questionId) {
        AccessDecision decision = decide(userId, questionId);
        return new SolutionEligibilityVO(
                decision.accepted(), decision.canWrite(), decision.canViewFull(), decision.admin());
    }

    record AccessDecision(boolean accepted, boolean canWrite, boolean canViewFull, boolean admin) {
        public static AccessDecision hintOnly() {
            return new AccessDecision(false, false, false, false);
        }
    }
}
