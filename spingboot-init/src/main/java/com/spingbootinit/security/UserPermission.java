package com.spingbootinit.security;

/** Fixed capabilities exposed to the frontend and enforced again by backend services. */
public enum UserPermission {
    QUESTION_AUTHOR,
    QUESTION_MANAGE_OWN,
    QUESTION_MANAGE_ALL,
    STUDENT_VIEW_ASSIGNED,
    STUDENT_VIEW_ALL,
    SUBMISSION_VIEW_ASSIGNED,
    SUBMISSION_VIEW_ALL,
    USER_ROLE_MANAGE,
    USER_BAN,
    TEACHER_STUDENT_ASSIGN,
    CONTEST_MANAGE,
    ANNOUNCEMENT_MANAGE,
    SOLUTION_REVIEW_ALL,
    AI_FULL_ANSWER_ALL
}
