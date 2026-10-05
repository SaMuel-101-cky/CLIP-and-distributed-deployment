package com.hw.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiTask {
    private Long id;
    private Long userId;
    private String taskType;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
