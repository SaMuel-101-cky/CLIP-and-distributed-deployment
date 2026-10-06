package com.hw.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmbeddingRecord {
    private Long id;
    private Long userId;
    private String targetType;
    private Long targetId;
    private String embeddingModel;
    private String vectorDb;
    private String collectionName;
    private String vectorId;
    private Integer dim;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
