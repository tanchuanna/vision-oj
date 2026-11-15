package com.spingbootinit.service;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.impl.TeachingStudentAccessService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeachingStudentAccessServiceTest {

    @Test
    void administratorUsesAllStudentsScope() {
        UserService users = mock(UserService.class);
        when(users.getById(1L)).thenReturn(user(1L, "admin"));
        TeachingStudentAccessService service = new TeachingStudentAccessService(
                users, mock(TeacherStudentService.class), new UserRolePolicy());

        assertTrue(service.resolve(1L).allStudents());
        assertTrue(service.resolve(1L).studentIds().isEmpty());
    }

    @Test
    void teacherUsesOnlyAssignedStudentIds() {
        UserService users = mock(UserService.class);
        TeacherStudentService relations = mock(TeacherStudentService.class);
        when(users.getById(2L)).thenReturn(user(2L, "teacher"));
        when(relations.activeStudentIds(2L)).thenReturn(List.of(10L, 11L));
        TeachingStudentAccessService service = new TeachingStudentAccessService(
                users, relations, new UserRolePolicy());

        var scope = service.resolve(2L);
        assertFalse(scope.allStudents());
        assertEquals(List.of(10L, 11L), scope.studentIds());
        assertDoesNotThrow(() -> service.requireStudentAccess(2L, 10L));
        assertThrows(BusinessException.class, () -> service.requireStudentAccess(2L, 99L));
    }

    @Test
    void studentCannotOpenTeachingStudentManagement() {
        UserService users = mock(UserService.class);
        when(users.getById(3L)).thenReturn(user(3L, "user"));
        TeachingStudentAccessService service = new TeachingStudentAccessService(
                users, mock(TeacherStudentService.class), new UserRolePolicy());

        assertThrows(BusinessException.class, () -> service.resolve(3L));
    }

    private static User user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        user.setStatus(1);
        return user;
    }
}
