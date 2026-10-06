package com.hw.pojo.dto;

import lombok.Data;

@Data
public class EmbeddingRecordQueryDto {
    private String status;
    private String embeddingModel;
    private Integer page;
    private Integer pageSize;
}
