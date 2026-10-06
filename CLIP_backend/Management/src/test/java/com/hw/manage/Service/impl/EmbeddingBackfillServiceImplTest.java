package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.AiTaskMapper;
import com.hw.manage.Mapper.EmbeddingRecordMapper;
import com.hw.manage.Mapper.PhotosMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.pojo.dto.AiEmbeddingBackfillDto;
import com.hw.pojo.dto.EmbeddingBackfillRequestDto;
import com.hw.pojo.entity.AiTask;
import com.hw.pojo.entity.Photos;
import com.hw.pojo.entity.User;
import com.hw.pojo.query.Result;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingBackfillServiceImplTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private PhotosMapper photosMapper;
    @Mock
    private AiTaskMapper aiTaskMapper;
    @Mock
    private EmbeddingRecordMapper embeddingRecordMapper;
    @Mock
    private RestTemplate restTemplate;

    @Test
    void startBackfillUsesAllActivePhotosWhenPhotoIdsAreMissing() {
        User user = new User();
        user.setId(7L);
        when(userMapper.findByUsername("alice")).thenReturn(user);
        when(photosMapper.listActivePhotoRecords(7L)).thenReturn(List.of(
                photo(11L, "D:/photos/one.png"),
                photo(12L, "D:/photos/two.png")
        ));
        doAnswer(invocation -> {
            invocation.getArgument(0, AiTask.class).setId(31L);
            return null;
        }).when(aiTaskMapper).insert(any(AiTask.class));
        when(restTemplate.postForEntity(anyString(), any(), eq(Result.class)))
                .thenReturn(new ResponseEntity<>(Result.success(), HttpStatus.OK));

        EmbeddingBackfillServiceImpl service = service();
        EmbeddingBackfillRequestDto request = new EmbeddingBackfillRequestDto();
        request.setUsername("alice");

        Map<String, Object> result = service.startBackfill(request);

        ArgumentCaptor<AiTask> taskCaptor = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskMapper).insert(taskCaptor.capture());
        assertEquals("EMBEDDING_BACKFILL", taskCaptor.getValue().getTaskType());
        assertEquals("PENDING", taskCaptor.getValue().getStatus());
        verify(aiTaskMapper).addPhoto(31L, 11L);
        verify(aiTaskMapper).addPhoto(31L, 12L);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AiEmbeddingBackfillDto> payloadCaptor = ArgumentCaptor.forClass(AiEmbeddingBackfillDto.class);
        verify(restTemplate).postForEntity(urlCaptor.capture(), payloadCaptor.capture(), eq(Result.class));
        assertTrue(urlCaptor.getValue().endsWith("/embeddings/backfill"));
        assertEquals(7L, payloadCaptor.getValue().getUserId());
        assertEquals(List.of(11L, 12L), payloadCaptor.getValue().getPhotosId());
        assertEquals(List.of("D:/photos/one.png", "D:/photos/two.png"), payloadCaptor.getValue().getPhotosList());
        assertEquals(31L, result.get("taskId"));
        assertEquals(2, result.get("photoCount"));
    }

    @Test
    void startBackfillRejectsPhotoIdsOutsideActiveUserSet() {
        User user = new User();
        user.setId(7L);
        when(userMapper.findByUsername("alice")).thenReturn(user);
        when(photosMapper.listActivePhotoRecordsByIds(7L, List.of(11L, 99L)))
                .thenReturn(List.of(photo(11L, "D:/photos/one.png")));

        EmbeddingBackfillServiceImpl service = service();
        EmbeddingBackfillRequestDto request = new EmbeddingBackfillRequestDto();
        request.setUsername("alice");
        request.setPhotoIds(List.of(11L, 99L));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.startBackfill(request));

        assertTrue(error.getMessage().contains("图片"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void startBackfillLogsCreatedAndDispatchedWithoutStoragePaths() {
        User user = new User();
        user.setId(7L);
        when(userMapper.findByUsername("alice")).thenReturn(user);
        when(photosMapper.listActivePhotoRecords(7L)).thenReturn(List.of(
                photo(11L, "D:/photos/one.png"), photo(12L, "D:/photos/two.png")
        ));
        doAnswer(invocation -> {
            invocation.getArgument(0, AiTask.class).setId(31L);
            return null;
        }).when(aiTaskMapper).insert(any(AiTask.class));
        when(restTemplate.postForEntity(anyString(), any(), eq(Result.class)))
                .thenReturn(new ResponseEntity<>(Result.success(), HttpStatus.OK));
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            EmbeddingBackfillRequestDto request = new EmbeddingBackfillRequestDto();
            request.setUsername("alice");
            service().startBackfill(request);

            String messages = messages(appender);
            assertTrue(messages.contains("event=embedding_backfill.created task_id=31 user_id=7 photo_count=2"));
            assertTrue(messages.contains("event=embedding_backfill.dispatched task_id=31 user_id=7 photo_count=2 status=RUNNING"));
            assertTrue(!messages.contains("D:/photos/one.png"));
        } finally {
            detachAppender(appender);
        }
    }

    private EmbeddingBackfillServiceImpl service() {
        EmbeddingBackfillServiceImpl service = new EmbeddingBackfillServiceImpl(
                userMapper, photosMapper, aiTaskMapper, restTemplate, embeddingRecordMapper);
        ReflectionTestUtils.setField(service, "aiServiceBaseUrl", "http://localhost:5000");
        ReflectionTestUtils.setField(service, "embeddingModel", "clip-vit-l-14");
        ReflectionTestUtils.setField(service, "vectorDb", "chroma");
        ReflectionTestUtils.setField(service, "collectionName", "clip_image_embeddings");
        return service;
    }

    private Photos photo(Long id, String storagePath) {
        Photos photo = new Photos();
        photo.setId(id);
        photo.setStoragePath(storagePath);
        photo.setStatus("ACTIVE");
        return photo;
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(EmbeddingBackfillServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(EmbeddingBackfillServiceImpl.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private String messages(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
    }
}
