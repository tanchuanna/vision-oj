package com.spingbootinit.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserRolePolicyTest {

    private final UserRolePolicy policy = new UserRolePolicy();

    @Test
    void administratorMatchingIsExactAndKeepsLegacyChineseAlias() {
        assertTrue(policy.isAdmin("admin"));
        assertTrue(policy.isAdmin("管理员"));
        assertFalse(policy.isAdmin("not_admin"));
        assertFalse(policy.isAdmin("administrator-helper"));
        assertFalse(policy.isAdmin("teacher"));
    }

    @Test
    void teacherReceivesTeachingAndPublishingCapabilitiesWithoutUserAdministration() {
        Set<UserPermission> permissions = policy.permissionsFor("teacher");

        assertTrue(permissions.contains(UserPermission.QUESTION_AUTHOR));
        assertTrue(permissions.contains(UserPermission.QUESTION_MANAGE_OWN));
        assertTrue(permissions.contains(UserPermission.STUDENT_VIEW_ASSIGNED));
        assertTrue(permissions.contains(UserPermission.SUBMISSION_VIEW_ASSIGNED));
        assertFalse(permissions.contains(UserPermission.USER_ROLE_MANAGE));
        assertFalse(permissions.contains(UserPermission.USER_BAN));
        assertTrue(permissions.contains(UserPermission.CONTEST_MANAGE));
        assertTrue(permissions.contains(UserPermission.ANNOUNCEMENT_MANAGE));
    }

    @Test
    void studentStorageValueRemainsUser() {
        assertEquals(UserRole.STUDENT, policy.resolve("user"));
        assertEquals(UserRole.STUDENT, policy.resolve("学生"));
        assertEquals(UserRole.TEACHER, policy.resolve("teacher"));
        assertEquals(UserRole.ADMIN, policy.resolve("admin"));
        assertEquals(UserRole.UNKNOWN, policy.resolve("unexpected"));
    }
}
