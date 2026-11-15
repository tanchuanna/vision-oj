package com.spingbootinit.controller;

import com.spingbootinit.context.UserContext.UserContext;
import com.spingbootinit.model.dto.question.solution.SolutionSaveRequest;
import com.spingbootinit.service.QuestionSolutionAccessService;
import com.spingbootinit.service.QuestionSolutionInteractionService;
import com.spingbootinit.service.QuestionSolutionService;
import com.spingbootinit.utils.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class QuestionSolutionControllerTest {
    private final QuestionSolutionService solutionService = mock(QuestionSolutionService.class);
    private final QuestionSolutionAccessService accessService = mock(QuestionSolutionAccessService.class);
    private final QuestionSolutionInteractionService interactionService = mock(QuestionSolutionInteractionService.class);
    private final JwtUtils jwtUtils = mock(JwtUtils.class);

    @AfterEach
    void clear() { UserContext.clear(); }

    @Test
    void saveAlwaysUsesAuthenticatedContextUser() {
        QuestionSolutionController controller = controller();
        UserContext.setCurrenUserId(77L);
        SolutionSaveRequest request = new SolutionSaveRequest();
        request.setQuestionId("11");
        when(solutionService.saveSolution(request, 77L)).thenReturn(99L);

        var result = controller.save(request);

        assertEquals("99", result.getData());
        verify(solutionService).saveSolution(request, 77L);
    }

    @Test
    void eligibilityUsesOptionalBearerViewerAndNeverRequestUserId() {
        QuestionSolutionController controller = controller();
        when(jwtUtils.validateToken("good")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("good")).thenReturn(7L);
        when(accessService.eligibility(7L, 11L)).thenReturn(
                new com.spingbootinit.model.vo.question.solution.SolutionEligibilityVO(true, true, true, false));

        var result = controller.eligibility("11", "Bearer good");

        assertEquals(true, result.getData().accepted());
        verify(accessService).eligibility(7L, 11L);
    }

    private QuestionSolutionController controller() {
        QuestionSolutionController controller = new QuestionSolutionController();
        ReflectionTestUtils.setField(controller, "solutionService", solutionService);
        ReflectionTestUtils.setField(controller, "accessService", accessService);
        ReflectionTestUtils.setField(controller, "interactionService", interactionService);
        ReflectionTestUtils.setField(controller, "jwtUtils", jwtUtils);
        return controller;
    }
}
