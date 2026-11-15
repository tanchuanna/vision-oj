package com.spingbootinit.model.vo.questionsubmitvo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class SubmissionOverviewVO {
    private long totalSubmissions;
    private long acceptedSubmissions;
    private long solvedProblems;
    private long activeUsers;
    private long pendingSubmissions;
    private double acceptedRate;
    private List<String> availableScopes = new ArrayList<>();
    private List<TrendPoint> trend = new ArrayList<>();
    private List<DistributionItem> verdictDistribution = new ArrayList<>();
    private List<DistributionItem> languageDistribution = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String date;
        private long total;
        private long accepted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistributionItem {
        private String name;
        private long value;
    }
}
