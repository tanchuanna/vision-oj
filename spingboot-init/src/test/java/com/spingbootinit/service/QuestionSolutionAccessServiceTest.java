package com.spingbootinit.service;

import com.spingbootinit.context.UserContext.IsAdminRoleString;
import com.spingbootinit.mapper.QuestionSubmitMapper;
import com.spingbootinit.model.entity.User;
import com.spingbootinit.service.impl.QuestionSolutionAccessServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class QuestionSolutionAccessServiceTest {
    private final QuestionSubmitMapper submitMapper = mock(QuestionSubmitMapper.class);
    private final UserService userService = mock(UserService.class);
    private QuestionSolutionAccessService service;

    @BeforeEach
    void setUp() {
        QuestionSolutionAccessServiceImpl impl = new QuestionSolutionAccessServiceImpl();
        ReflectionTestUtils.setField(impl, "questionSubmitMapper", submitMapper);
        ReflectionTestUtils.setField(impl, "userService", userService);
        ReflectionTestUtils.setField(impl, "isAdminRole", new IsAdminRoleString());
        service = impl;
    }

    @Test
    void exactUserAndQuestionAcceptedUnlocksFullSolution() {
        when(submitMapper.existsAccepted(7L, 11L)).thenReturn(true);
        when(userService.getById(7L)).thenReturn(user("user"));

        var result = service.decide(7L, 11L);

        assertTrue(result.accepted());
        assertTrue(result.canWrite());
        assertTrue(result.canViewFull());
        assertFalse(result.admin());
    }

    @Test
    void anotherQuestionOrUserDoesNotUnlockCurrentQuestion() {
        when(submitMapper.existsAccepted(7L, 11L)).thenReturn(false);
        when(userService.getById(7L)).thenReturn(user("user"));

        var result = service.decide(7L, 11L);

        assertFalse(result.accepted());
        assertFalse(result.canWrite());
        assertFalse(result.canViewFull());
        verify(submitMapper).existsAccepted(7L, 11L);
    }

    @Test
    void anonymousViewerOnlyGetsHintAccess() {
        var result = service.decide(null, 11L);

        assertFalse(result.accepted());
        assertFalse(result.canWrite());
        assertFalse(result.canViewFull());
        verifyNoInteractions(submitMapper, userService);
    }

    @Test
    void administratorBypassesAcceptedRequirement() {
        when(userService.getById(9L)).thenReturn(user("admin"));

        var result = service.decide(9L, 11L);

        assertTrue(result.admin());
        assertTrue(result.canWrite());
        assertTrue(result.canViewFull());
        assertFalse(result.accepted());
        verify(submitMapper, never()).existsAccepted(anyLong(), anyLong());
    }

    @Test
    void invalidQuestionFailsClosed() {
        var result = service.decide(7L, 0L);
        assertFalse(result.canViewFull());
        verifyNoInteractions(submitMapper, userService);
    }

    private static User user(String role) {
        User user = new User();
        user.setUserRole(role);
        return user;
    }
}
