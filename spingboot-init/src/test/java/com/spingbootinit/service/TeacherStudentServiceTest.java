package com.spingbootinit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.mapper.TeacherStudentRelationMapper;
import com.spingbootinit.model.entity.TeacherStudentRelation;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.impl.TeacherStudentServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeacherStudentServiceTest {

    @Test
    void administratorCanBindTeacherAndStudentAndExistingRelationIsReactivated() {
        UserService users = mock(UserService.class);
        TeacherStudentRelationMapper mapper = mock(TeacherStudentRelationMapper.class);
        User admin = user(1L, "admin");
        User teacher = user(2L, "teacher");
        User student = user(3L, "user");
        when(users.getById(1L)).thenReturn(admin);
        when(users.getById(2L)).thenReturn(teacher);
        when(users.getById(3L)).thenReturn(student);
        TeacherStudentRelation existing = new TeacherStudentRelation();
        existing.setId(9L);
        existing.setTeacherId(2L);
        existing.setStudentId(3L);
        existing.setStatus(0);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        TeacherStudentServiceImpl service = new TeacherStudentServiceImpl(mapper, users, new UserRolePolicy());

        service.bind(1L, 2L, 3L);

        assertEquals(1, existing.getStatus());
        assertEquals(1L, existing.getAssignedBy());
        assertNull(existing.getUnassignedAt());
        verify(mapper).updateById(existing);
        verify(mapper, never()).insert(any(TeacherStudentRelation.class));
    }

    @Test
    void teacherCannotBindStudents() {
        UserService users = mock(UserService.class);
        when(users.getById(2L)).thenReturn(user(2L, "teacher"));
        TeacherStudentServiceImpl service = new TeacherStudentServiceImpl(
                mock(TeacherStudentRelationMapper.class), users, new UserRolePolicy());

        assertThrows(BusinessException.class, () -> service.bind(2L, 2L, 3L));
    }

    @Test
    void teacherScopeContainsOnlyActiveAssignedStudents() {
        UserService users = mock(UserService.class);
        TeacherStudentRelationMapper mapper = mock(TeacherStudentRelationMapper.class);
        TeacherStudentRelation first = relation(2L, 10L, 1);
        TeacherStudentRelation inactive = relation(2L, 11L, 0);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, inactive));
        TeacherStudentServiceImpl service = new TeacherStudentServiceImpl(mapper, users, new UserRolePolicy());

        assertEquals(List.of(10L), service.activeStudentIds(2L));
        assertTrue(service.canTeacherAccessStudent(2L, 10L));
        assertFalse(service.canTeacherAccessStudent(2L, 99L));
    }

    private static User user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        user.setStatus(1);
        return user;
    }

    private static TeacherStudentRelation relation(long teacherId, long studentId, int status) {
        TeacherStudentRelation relation = new TeacherStudentRelation();
        relation.setTeacherId(teacherId);
        relation.setStudentId(studentId);
        relation.setStatus(status);
        return relation;
    }
}
