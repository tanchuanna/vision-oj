package com.spingbootinit.service;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.context.UserContext.IsAdminRoleString;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.service.impl.SubmissionAccessScopeService;
import com.spingbootinit.service.impl.TeachingStudentAccessService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubmissionAccessScopeServiceTest {

    @Test
    void selfScopeAlwaysPinsCurrentUser() {
        UserService userService = mock(UserService.class);
        IsAdminRoleString adminRole = mock(IsAdminRoleString.class);
        SubmissionAccessScopeService service = new SubmissionAccessScopeService(userService, adminRole);

        var access = service.resolve(42L, "SELF");

        assertEquals(42L, access.ownerUserId());
        assertFalse(access.managed());
        verifyNoInteractions(userService, adminRole);
    }

    @Test
    void managedScopeRejectsOrdinaryUser() {
        UserService userService = mock(UserService.class);
        IsAdminRoleString adminRole = mock(IsAdminRoleString.class);
        User user = new User();
        user.setUserRole("user");
        when(userService.getById(42L)).thenReturn(user);
        when(adminRole.isAdminRoleString("user")).thenReturn(false);
        SubmissionAccessScopeService service = new SubmissionAccessScopeService(userService, adminRole);

        assertThrows(BusinessException.class, () -> service.resolve(42L, "MANAGED"));
    }

    @Test
    void managedScopeAllowsAdministratorWithoutOwnerRestriction() {
        UserService userService = mock(UserService.class);
        IsAdminRoleString adminRole = mock(IsAdminRoleString.class);
        User user = new User();
        user.setUserRole("admin");
        when(userService.getById(7L)).thenReturn(user);
        when(adminRole.isAdminRoleString("admin")).thenReturn(true);
        SubmissionAccessScopeService service = new SubmissionAccessScopeService(userService, adminRole);

        var access = service.resolve(7L, "MANAGED");

        assertNull(access.ownerUserId());
        assertTrue(access.managed());
        assertTrue(access.allManagedUsers());
    }

    @Test
    void managedScopeForTeacherIsRestrictedToAssignedStudents() {
        UserService userService = mock(UserService.class);
        IsAdminRoleString adminRole = mock(IsAdminRoleString.class);
        TeachingStudentAccessService teachingAccess = mock(TeachingStudentAccessService.class);
        User teacher = new User();
        teacher.setUserRole("teacher");
        when(userService.getById(8L)).thenReturn(teacher);
        when(teachingAccess.resolve(8L)).thenReturn(
                new TeachingStudentAccessService.Scope(false, List.of(10L, 11L)));
        SubmissionAccessScopeService service = new SubmissionAccessScopeService(
                userService, adminRole, teachingAccess, new com.spingbootinit.security.UserRolePolicy());

        var access = service.resolve(8L, "MANAGED");

        assertTrue(access.managed());
        assertFalse(access.allManagedUsers());
        assertEquals(List.of(10L, 11L), access.managedUserIds());
    }
}
