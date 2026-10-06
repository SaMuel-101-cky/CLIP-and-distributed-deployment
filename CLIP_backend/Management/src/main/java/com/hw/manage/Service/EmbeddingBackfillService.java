package com.hw.manage.Service;

import com.hw.pojo.dto.EmbeddingBackfillRequestDto;

import java.util.Map;

public interface EmbeddingBackfillService {
    Map<String, Object> startBackfill(EmbeddingBackfillRequestDto request);
}
