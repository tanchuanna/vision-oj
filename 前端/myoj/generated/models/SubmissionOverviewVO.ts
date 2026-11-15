/* generated using openapi-typescript-codegen -- do not edit */
export type SubmissionOverviewVO = {
    totalSubmissions?: number;
    acceptedSubmissions?: number;
    solvedProblems?: number;
    activeUsers?: number;
    pendingSubmissions?: number;
    acceptedRate?: number;
    availableScopes?: Array<string>;
    trend?: Array<{ date?: string; total?: number; accepted?: number }>;
    verdictDistribution?: Array<{ name?: string; value?: number }>;
    languageDistribution?: Array<{ name?: string; value?: number }>;
};
