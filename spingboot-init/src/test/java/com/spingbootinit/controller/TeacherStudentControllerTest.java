package com.spingbootinit.controller;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.security.UserRolePolicy;
import com.spingbootinit.service.TeacherStudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TeacherStudentControllerTest {
    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    @Test
    void teacherCannotReadAnotherTeachersAssignmentList() {
        UserContext.setCurrenUserId(5L);
        UserContext.setCurrentRole("teacher");
        TeacherStudentController controller = new TeacherStudentController(
                mock(TeacherStudentService.class), new UserRolePolicy());

        assertThrows(BusinessException.class, () -> controller.assignedStudentIds(6L));
    }
}
