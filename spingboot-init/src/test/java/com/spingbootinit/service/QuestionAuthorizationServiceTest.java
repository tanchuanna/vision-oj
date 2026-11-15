package com.spingbootinit.service;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.mapper.QuestionMapper;
import com.spingbootinit.model.entity.Question;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.impl.QuestionAuthorizationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionAuthorizationServiceTest {
    @Test void teacherCanManageOnlyOwnedQuestion() {
        UserService users = mock(UserService.class);
        QuestionMapper questions = mock(QuestionMapper.class);
        User teacher = new User(); teacher.setId(7L); teacher.setUserRole("teacher"); teacher.setStatus(1);
        when(users.getById(7L)).thenReturn(teacher);
        Question own = new Question(); own.setId(11L); own.setUserId(7L);
        Question other = new Question(); other.setId(12L); other.setUserId(8L);
        when(questions.selectById(11L)).thenReturn(own);
        when(questions.selectById(12L)).thenReturn(other);
        var service = new QuestionAuthorizationService(users, new UserRolePolicy(), questions);
        assertDoesNotThrow(() -> service.requireCanManage(7L, 11L));
        assertThrows(BusinessException.class, () -> service.requireCanManage(7L, 12L));
    }

    @Test void similarAdminStringHasNoPrivileges() {
        UserService users = mock(UserService.class);
        QuestionMapper questions = mock(QuestionMapper.class);
        User impostor = new User(); impostor.setId(9L); impostor.setUserRole("not_admin"); impostor.setStatus(1);
        when(users.getById(9L)).thenReturn(impostor);
        var service = new QuestionAuthorizationService(users, new UserRolePolicy(), questions);
        assertThrows(BusinessException.class, () -> service.requireAuthor(9L));
    }
}
