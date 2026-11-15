package com.spingbootinit.model.vo.question.solution;

public record SolutionEligibilityVO(
        boolean accepted,
        boolean canWrite,
        boolean canViewFull,
        boolean admin
) {
}
