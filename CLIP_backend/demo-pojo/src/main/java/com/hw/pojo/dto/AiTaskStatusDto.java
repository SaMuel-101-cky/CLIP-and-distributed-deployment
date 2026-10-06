package com.hw.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiTaskStatusDto {
    private Long taskId;
    private String taskType;
    private String status;
    private String errorMessage;
    private Integer photoCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
