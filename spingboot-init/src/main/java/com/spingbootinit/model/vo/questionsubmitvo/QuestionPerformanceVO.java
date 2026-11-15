package com.spingbootinit.model.vo.questionsubmitvo;

import lombok.Data;

@Data
public class QuestionPerformanceVO {
    private Long runtimeMs;
    private Long memoryKb;
}
