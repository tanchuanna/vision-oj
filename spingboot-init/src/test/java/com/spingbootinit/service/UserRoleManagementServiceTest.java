package com.spingbootinit.service;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.impl.UserRoleManagementService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UserRoleManagementServiceTest {

    @Test
    void administratorCanPromoteStudentAndRoleChangeInvalidatesRelationsAndUiRevision() {
        UserService users = mock(UserService.class);
        TeacherStudentService relations = mock(TeacherStudentService.class);
        UserStatusNotifyService notify = mock(UserStatusNotifyService.class);
        User admin = user(1L, "admin");
        User student = user(2L, "user");
        when(users.getById(1L)).thenReturn(admin);
        when(users.getById(2L)).thenReturn(student);
        when(users.updateById(student)).thenReturn(true);
        UserRoleManagementService service = new UserRoleManagementService(
                users, relations, notify, new UserRolePolicy());

        service.changeRole(1L, 2L, "teacher");

        assertEquals("teacher", student.getUserRole());
        verify(relations).deactivateRelationsForRoleChange(2L);
        verify(notify).bumpStatusRevision(2L);
    }

    @Test
    void roleManagementCannotTargetAdministratorOrSelf() {
        UserService users = mock(UserService.class);
        User admin = user(1L, "admin");
        when(users.getById(1L)).thenReturn(admin);
        UserRoleManagementService service = new UserRoleManagementService(
                users, mock(TeacherStudentService.class), mock(UserStatusNotifyService.class),
                new UserRolePolicy());

        assertThrows(BusinessException.class, () -> service.changeRole(1L, 1L, "user"));
    }

    @Test
    void teacherCannotChangeRoles() {
        UserService users = mock(UserService.class);
        when(users.getById(7L)).thenReturn(user(7L, "teacher"));
        UserRoleManagementService service = new UserRoleManagementService(
                users, mock(TeacherStudentService.class), mock(UserStatusNotifyService.class),
                new UserRolePolicy());

        assertThrows(BusinessException.class, () -> service.changeRole(7L, 8L, "teacher"));
    }

    private static User user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        user.setStatus(1);
        return user;
    }
}
