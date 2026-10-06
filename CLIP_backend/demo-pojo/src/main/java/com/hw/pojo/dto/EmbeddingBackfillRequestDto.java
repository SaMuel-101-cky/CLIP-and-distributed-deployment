package com.hw.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class EmbeddingBackfillRequestDto {
    private String username;
    private List<Long> photoIds;
}
