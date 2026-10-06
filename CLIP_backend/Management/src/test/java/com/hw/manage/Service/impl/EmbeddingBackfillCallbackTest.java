package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.AiTaskMapper;
import com.hw.manage.Mapper.EmbeddingRecordMapper;
import com.hw.manage.Mapper.PhotosMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.pojo.dto.AiEmbeddingRecordDto;
import com.hw.pojo.dto.AiEmbeddingResultDto;
import com.hw.pojo.entity.AiTask;
import com.hw.pojo.entity.EmbeddingRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingBackfillCallbackTest {
    @Mock private UserMapper userMapper;
    @Mock private PhotosMapper photosMapper;
    @Mock private AiTaskMapper aiTaskMapper;
    @Mock private EmbeddingRecordMapper embeddingRecordMapper;
    @Mock private RestTemplate restTemplate;

    @Test
    void saveEmbeddingResultUpsertsRecordsAndMarksSuccess() {
        AiTask task = task("EMBEDDING_BACKFILL");
        when(aiTaskMapper.findById(31L)).thenReturn(task);
        when(aiTaskMapper.countTaskPhoto(31L, 11L)).thenReturn(1);

        service().saveEmbeddingResult(31L, successResult());

        ArgumentCaptor<EmbeddingRecord> record = ArgumentCaptor.forClass(EmbeddingRecord.class);
        verify(embeddingRecordMapper).upsert(record.capture());
        assertEquals("PHOTO", record.getValue().getTargetType());
        assertEquals(11L, record.getValue().getTargetId());
        assertEquals(7L, record.getValue().getUserId());
        verify(aiTaskMapper).updateStatus(31L, "SUCCESS", null);
    }

    @Test
    void saveEmbeddingResultRejectsWrongTaskType() {
        when(aiTaskMapper.findById(31L)).thenReturn(task("TEXT_SEARCH"));

        assertThrows(IllegalArgumentException.class, () -> service().saveEmbeddingResult(31L, successResult()));

        verify(embeddingRecordMapper, never()).upsert(any());
    }

    @Test
    void saveEmbeddingResultMarksFailedOnFailureCallback() {
        when(aiTaskMapper.findById(31L)).thenReturn(task("EMBEDDING_BACKFILL"));
        AiEmbeddingResultDto failed = new AiEmbeddingResultDto();
        failed.setStatus("FAILED");
        failed.setErrorMessage("vector store disabled");

        service().saveEmbeddingResult(31L, failed);

        verify(aiTaskMapper).updateStatus(31L, "FAILED", "vector store disabled");
        verify(embeddingRecordMapper, never()).upsert(any());
    }

    private EmbeddingBackfillServiceImpl service() {
        return new EmbeddingBackfillServiceImpl(userMapper, photosMapper, aiTaskMapper, restTemplate, embeddingRecordMapper);
    }

    private AiTask task(String type) {
        AiTask task = new AiTask();
        task.setId(31L);
        task.setUserId(7L);
        task.setTaskType(type);
        return task;
    }

    private AiEmbeddingResultDto successResult() {
        AiEmbeddingRecordDto record = new AiEmbeddingRecordDto();
        record.setPhotoId(11L);
        record.setTargetType("PHOTO");
        record.setEmbeddingModel("clip-vit-l-14");
        record.setVectorDb("chroma");
        record.setCollectionName("clip_image_embeddings");
        record.setVectorId("photo:11:clip-vit-l-14");
        record.setDim(768);
        record.setStatus("READY");
        AiEmbeddingResultDto result = new AiEmbeddingResultDto();
        result.setTaskId(31L);
        result.setStatus("SUCCESS");
        result.setRecords(List.of(record));
        return result;
    }
}
