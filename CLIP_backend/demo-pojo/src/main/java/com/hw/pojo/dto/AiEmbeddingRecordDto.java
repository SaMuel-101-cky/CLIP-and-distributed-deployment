package com.hw.pojo.dto;

import lombok.Data;

@Data
public class AiEmbeddingRecordDto {
    private Long photoId;
    private String targetType;
    private String embeddingModel;
    private String vectorDb;
    private String collectionName;
    private String vectorId;
    private Integer dim;
    private String status;
}
