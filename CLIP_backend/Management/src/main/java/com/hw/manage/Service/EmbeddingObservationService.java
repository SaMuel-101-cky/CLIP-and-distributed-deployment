package com.hw.manage.Service;

import com.hw.pojo.dto.EmbeddingRecordDto;
import com.hw.pojo.dto.EmbeddingRecordQueryDto;
import com.hw.pojo.query.PageResult;

public interface EmbeddingObservationService {
    PageResult<EmbeddingRecordDto> listRecords(String username, EmbeddingRecordQueryDto query);
}
