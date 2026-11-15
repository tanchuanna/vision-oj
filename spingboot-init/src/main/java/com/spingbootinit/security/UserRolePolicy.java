package com.spingbootinit.security;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Single source of truth for the fixed student/teacher/administrator permission matrix. */
@Component
public class UserRolePolicy {

    private static final Set<UserPermission> TEACHER_PERMISSIONS = Collections.unmodifiableSet(EnumSet.of(
            UserPermission.QUESTION_AUTHOR,
            UserPermission.QUESTION_MANAGE_OWN,
            UserPermission.STUDENT_VIEW_ASSIGNED,
            UserPermission.SUBMISSION_VIEW_ASSIGNED,
            UserPermission.CONTEST_MANAGE,
            UserPermission.ANNOUNCEMENT_MANAGE
    ));

    private static final Set<UserPermission> ADMIN_PERMISSIONS =
            Collections.unmodifiableSet(EnumSet.allOf(UserPermission.class));

    public UserRole resolve(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return UserRole.UNKNOWN;
        }
        String value = rawRole.trim();
        String normalized = value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "user", "student" -> UserRole.STUDENT;
            case "teacher" -> UserRole.TEACHER;
            case "admin" -> UserRole.ADMIN;
            default -> {
                if ("学生".equals(value) || "普通用户".equals(value)) yield UserRole.STUDENT;
                if ("教师".equals(value) || "老师".equals(value)) yield UserRole.TEACHER;
                if ("管理员".equals(value)) yield UserRole.ADMIN;
                yield UserRole.UNKNOWN;
            }
        };
    }

    public boolean isAdmin(String role) {
        return resolve(role) == UserRole.ADMIN;
    }

    public boolean isTeacher(String role) {
        return resolve(role) == UserRole.TEACHER;
    }

    public boolean canAuthorQuestions(String role) {
        UserRole resolved = resolve(role);
        return resolved == UserRole.TEACHER || resolved == UserRole.ADMIN;
    }

    public boolean hasPermission(String role, UserPermission permission) {
        return permissionsFor(role).contains(permission);
    }

    public void requirePermission(String role, UserPermission permission) {
        if (!hasPermission(role, permission)) {
            throw new BusinessException(ResultCode.NO_AUTH_ERROR);
        }
    }

    public Set<UserPermission> permissionsFor(String role) {
        return switch (resolve(role)) {
            case ADMIN -> ADMIN_PERMISSIONS;
            case TEACHER -> TEACHER_PERMISSIONS;
            default -> Set.of();
        };
    }

    public Set<String> permissionNamesFor(String role) {
        return permissionsFor(role).stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
