package com.hw.pojo.entity;

import lombok.Data;

@Data
public class BenchmarkRun {
    private Long id;
    private String runId;
    private String status;
    private String resolvedPlanJson;
    private String errorType;
}
