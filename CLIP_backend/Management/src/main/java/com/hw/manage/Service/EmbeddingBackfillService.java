package com.hw.manage.Service;

import com.hw.pojo.dto.EmbeddingBackfillRequestDto;
import com.hw.pojo.dto.AiEmbeddingResultDto;

import java.util.Map;

public interface EmbeddingBackfillService {
    Map<String, Object> startBackfill(EmbeddingBackfillRequestDto request);

    void saveEmbeddingResult(Long taskId, AiEmbeddingResultDto result);
}
