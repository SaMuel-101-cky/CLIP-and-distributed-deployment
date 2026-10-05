package com.hw.pojo.dto;

import lombok.Data;

@Data
public class AiMatchDto {
    private Long photoId;
    private Long descriptionId;
    private String matchType;
    private Double score;
    private Integer rankNo;
}
