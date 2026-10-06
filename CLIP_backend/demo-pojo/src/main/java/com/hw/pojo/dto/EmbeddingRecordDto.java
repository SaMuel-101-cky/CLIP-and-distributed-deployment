package com.hw.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmbeddingRecordDto {
    private Long targetId;
    private String targetType;
    private String embeddingModel;
    private String vectorDb;
    private String collectionName;
    private String vectorId;
    private Integer dim;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
