package com.hw.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiMatchResultDto {
    private Long taskId;
    private String status;
    private String errorMessage;
    private List<AiMatchDto> matches;
}
