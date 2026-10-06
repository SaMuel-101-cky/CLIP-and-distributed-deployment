package com.hw.pojo.entity;

import lombok.Data;

@Data
public class BenchmarkSample {
    private Long id;
    private String runId;
    private Integer sequenceNo;
    private Boolean warmup;
    private Double totalMs;
    private Boolean success;
    private String errorType;
}
