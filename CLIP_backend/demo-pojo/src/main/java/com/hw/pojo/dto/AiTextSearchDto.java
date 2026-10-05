package com.hw.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiTextSearchDto {
    private Long taskId;
    private Long userId;
    private String description;
    private Long descriptionId;
    private List<String> photosList;
    private List<Long> photosId;
}
