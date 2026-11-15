package com.spingbootinit.model.vo.questionsubmitvo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionStatisticsVO {
    private Long totalSubmissions;
    private Long acceptedSubmissions;
    private Long failedSubmissions;
    private Long acceptedUsers;
    private Double passRate;
    private Double beatRuntimePercent;
    private Double beatMemoryPercent;
    private Boolean acceptedByCurrentUser;
    private List<QuestionRankingItemVO> fastestRankings = new ArrayList<>();
    private List<QuestionRankingItemVO> memoryRankings = new ArrayList<>();
}
