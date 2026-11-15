package com.spingbootinit.service;

import com.spingbootinit.mapper.QuestionSubmitMapper;
import com.spingbootinit.model.vo.questionsubmitvo.QuestionPerformanceVO;
import com.spingbootinit.model.vo.questionsubmitvo.QuestionRankingItemVO;
import com.spingbootinit.model.vo.questionsubmitvo.QuestionStatisticsVO;
import com.spingbootinit.service.impl.QuestionSubmitServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestionStatisticsServiceTest {

    @Test
    void statisticsCalculatesPercentilesAndKeepsOneRankingRowPerUser() {
        QuestionSubmitMapper mapper = mock(QuestionSubmitMapper.class);
        QuestionStatisticsVO aggregate = new QuestionStatisticsVO();
        aggregate.setTotalSubmissions(74L);
        aggregate.setAcceptedSubmissions(16L);
        aggregate.setAcceptedUsers(10L);
        QuestionPerformanceVO mine = new QuestionPerformanceVO();
        mine.setRuntimeMs(20L);
        mine.setMemoryKb(2048L);
        QuestionRankingItemVO first = new QuestionRankingItemVO();
        first.setUserName("小码");

        when(mapper.selectQuestionStatistics(99L)).thenReturn(aggregate);
        when(mapper.selectFastestRanking(99L, 20)).thenReturn(List.of(first));
        when(mapper.selectMemoryRanking(99L, 20)).thenReturn(List.of(first));
        when(mapper.selectUserBestPerformance(99L, 7L)).thenReturn(mine);
        when(mapper.countUsersSlowerThanRuntime(99L, 20L)).thenReturn(7L);
        when(mapper.countUsersUsingMoreMemory(99L, 2048L)).thenReturn(6L);

        QuestionSubmitServiceImpl service = new QuestionSubmitServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        QuestionStatisticsVO result = service.getQuestionStatistics(99L, 7L);

        assertEquals(58L, result.getFailedSubmissions());
        assertEquals(21.62, result.getPassRate(), 0.001);
        assertEquals(70.0, result.getBeatRuntimePercent(), 0.001);
        assertEquals(60.0, result.getBeatMemoryPercent(), 0.001);
        assertTrue(result.getAcceptedByCurrentUser());
        assertEquals(1, result.getFastestRankings().size());
        verify(mapper).selectFastestRanking(99L, 20);
    }

    @Test
    void statisticsLeavesPersonalPercentilesNullForGuestOrUserWithoutAcceptedRun() {
        QuestionSubmitMapper mapper = mock(QuestionSubmitMapper.class);
        QuestionStatisticsVO aggregate = new QuestionStatisticsVO();
        aggregate.setTotalSubmissions(0L);
        aggregate.setAcceptedSubmissions(0L);
        aggregate.setAcceptedUsers(0L);
        when(mapper.selectQuestionStatistics(5L)).thenReturn(aggregate);
        when(mapper.selectFastestRanking(5L, 20)).thenReturn(List.of());
        when(mapper.selectMemoryRanking(5L, 20)).thenReturn(List.of());

        QuestionSubmitServiceImpl service = new QuestionSubmitServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        QuestionStatisticsVO result = service.getQuestionStatistics(5L, null);

        assertEquals(0.0, result.getPassRate());
        assertNull(result.getBeatRuntimePercent());
        assertNull(result.getBeatMemoryPercent());
        assertFalse(result.getAcceptedByCurrentUser());
        verify(mapper, never()).selectUserBestPerformance(anyLong(), anyLong());
    }
}
