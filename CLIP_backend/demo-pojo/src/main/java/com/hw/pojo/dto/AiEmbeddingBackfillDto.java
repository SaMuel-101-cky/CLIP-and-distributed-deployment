package com.hw.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiEmbeddingBackfillDto {
    private Long taskId;
    private Long userId;
    private List<Long> photosId;
    private List<String> photosList;
    private String embeddingModel;
    private String vectorDb;
    private String collectionName;
}
