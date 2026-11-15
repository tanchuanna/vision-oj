package com.spingbootinit.service;

import com.spingbootinit.mapper.QuestionMapper;
import com.spingbootinit.model.entity.Question;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.impl.AiQuestionAccessService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiQuestionAccessServiceTest {
    @Test void teacherGetsFullAnswerOnlyForOwnQuestion() {
        UserService users = mock(UserService.class);
        QuestionMapper questions = mock(QuestionMapper.class);
        User teacher = new User(); teacher.setId(3L); teacher.setUserRole("teacher");
        when(users.getById(3L)).thenReturn(teacher);
        Question own = new Question(); own.setUserId(3L);
        Question other = new Question(); other.setUserId(4L);
        when(questions.selectById(21L)).thenReturn(own);
        when(questions.selectById(22L)).thenReturn(other);
        var service = new AiQuestionAccessService(users, questions, new UserRolePolicy());
        assertTrue(service.canReceiveFullAnswer(3L, "21"));
        assertFalse(service.canReceiveFullAnswer(3L, "22"));
        assertFalse(service.canReceiveFullAnswer(3L, null));
    }
}
