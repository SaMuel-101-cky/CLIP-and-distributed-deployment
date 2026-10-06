package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.AiTaskMapper;
import com.hw.manage.Mapper.EmbeddingRecordMapper;
import com.hw.manage.Mapper.PhotosMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.manage.Service.EmbeddingBackfillService;
import com.hw.pojo.dto.AiEmbeddingBackfillDto;
import com.hw.pojo.dto.AiEmbeddingRecordDto;
import com.hw.pojo.dto.AiEmbeddingResultDto;
import com.hw.pojo.dto.EmbeddingBackfillRequestDto;
import com.hw.pojo.entity.AiTask;
import com.hw.pojo.entity.EmbeddingRecord;
import com.hw.pojo.entity.Photos;
import com.hw.pojo.entity.User;
import com.hw.pojo.query.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingBackfillServiceImpl implements EmbeddingBackfillService {
    private final UserMapper userMapper;
    private final PhotosMapper photosMapper;
    private final AiTaskMapper aiTaskMapper;
    private final RestTemplate restTemplate;
    private final EmbeddingRecordMapper embeddingRecordMapper;

    @Value("${ai.service.base-url:http://localhost:5000}")
    private String aiServiceBaseUrl;

    @Value("${ai.embedding.model:clip-vit-l-14}")
    private String embeddingModel;

    @Value("${ai.vector.db:chroma}")
    private String vectorDb;

    @Value("${ai.vector.collection:clip_image_embeddings}")
    private String collectionName;

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = RuntimeException.class)
    public Map<String, Object> startBackfill(EmbeddingBackfillRequestDto request) {
        if (request == null || !StringUtils.hasText(request.getUsername())) {
            throw new IllegalArgumentException("username 不能为空");
        }

        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + request.getUsername());
        }

        List<Photos> photos = selectActivePhotos(user.getId(), request.getPhotoIds());
        if (CollectionUtils.isEmpty(photos)) {
            throw new IllegalArgumentException("没有可回填的有效图片");
        }

        AiTask task = new AiTask();
        task.setUserId(user.getId());
        task.setTaskType("EMBEDDING_BACKFILL");
        task.setStatus("PENDING");
        aiTaskMapper.insert(task);

        List<Long> photoIds = photos.stream().map(Photos::getId).toList();
        List<String> photoPaths = photos.stream().map(Photos::getStoragePath).toList();
        for (Long photoId : photoIds) {
            aiTaskMapper.addPhoto(task.getId(), photoId);
        }

        AiEmbeddingBackfillDto payload = new AiEmbeddingBackfillDto();
        payload.setTaskId(task.getId());
        payload.setUserId(user.getId());
        payload.setPhotosId(photoIds);
        payload.setPhotosList(photoPaths);
        payload.setEmbeddingModel(embeddingModel);
        payload.setVectorDb(vectorDb);
        payload.setCollectionName(collectionName);

        try {
            String url = aiServiceBaseUrl + "/embeddings/backfill";
            ResponseEntity<Result> response = restTemplate.postForEntity(url, payload, Result.class);
            Result aiResult = response.getBody();
            if (aiResult == null || aiResult.getCode() != 1) {
                String message = aiResult != null && StringUtils.hasText(aiResult.getMessage())
                        ? aiResult.getMessage()
                        : "空响应";
                throw new IllegalStateException("AI端返回失败: " + message);
            }
            aiTaskMapper.updateStatus(task.getId(), "RUNNING", null);
        } catch (RuntimeException e) {
            aiTaskMapper.updateStatus(task.getId(), "FAILED", e.getMessage());
            throw e;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("photoCount", photos.size());
        result.put("msg", "Embedding backfill task accepted");
        return result;
    }

    private List<Photos> selectActivePhotos(Long userId, List<Long> requestedPhotoIds) {
        if (CollectionUtils.isEmpty(requestedPhotoIds)) {
            return photosMapper.listActivePhotoRecords(userId);
        }

        List<Photos> photos = photosMapper.listActivePhotoRecordsByIds(userId, requestedPhotoIds);
        if (photos.size() != requestedPhotoIds.size()) {
            throw new IllegalArgumentException("请求图片包含无效、已删除或不属于该用户的图片");
        }
        return photos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveEmbeddingResult(Long taskId, AiEmbeddingResultDto result) {
        if (result == null) {
            throw new IllegalArgumentException("embedding 回调不能为空");
        }
        if (result.getTaskId() != null && !taskId.equals(result.getTaskId())) {
            throw new IllegalArgumentException("路径 taskId 与请求体 taskId 不一致");
        }

        AiTask task = aiTaskMapper.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("AI任务不存在: " + taskId);
        }
        if (!"EMBEDDING_BACKFILL".equals(task.getTaskType())) {
            throw new IllegalArgumentException("AI任务类型不是 EMBEDDING_BACKFILL");
        }
        if ("FAILED".equalsIgnoreCase(result.getStatus())) {
            aiTaskMapper.updateStatus(taskId, "FAILED", result.getErrorMessage());
            return;
        }
        if (!"SUCCESS".equalsIgnoreCase(result.getStatus())) {
            throw new IllegalArgumentException("embedding 回调状态无效");
        }
        if (CollectionUtils.isEmpty(result.getRecords())) {
            throw new IllegalArgumentException("SUCCESS embedding 回调 records 不能为空");
        }

        for (AiEmbeddingRecordDto record : result.getRecords()) {
            validateEmbeddingRecord(task, record);
            EmbeddingRecord entity = new EmbeddingRecord();
            entity.setUserId(task.getUserId());
            entity.setTargetType(record.getTargetType());
            entity.setTargetId(record.getPhotoId());
            entity.setEmbeddingModel(record.getEmbeddingModel());
            entity.setVectorDb(record.getVectorDb());
            entity.setCollectionName(record.getCollectionName());
            entity.setVectorId(record.getVectorId());
            entity.setDim(record.getDim());
            entity.setStatus(record.getStatus());
            embeddingRecordMapper.upsert(entity);
        }
        aiTaskMapper.updateStatus(taskId, "SUCCESS", null);
    }

    private void validateEmbeddingRecord(AiTask task, AiEmbeddingRecordDto record) {
        if (record == null || record.getPhotoId() == null || record.getDim() == null
                || !"PHOTO".equals(record.getTargetType())
                || !StringUtils.hasText(record.getEmbeddingModel())
                || !StringUtils.hasText(record.getVectorDb())
                || !StringUtils.hasText(record.getCollectionName())
                || !StringUtils.hasText(record.getVectorId())
                || !StringUtils.hasText(record.getStatus())) {
            throw new IllegalArgumentException("embedding 记录字段不完整或 targetType 无效");
        }
        Integer count = aiTaskMapper.countTaskPhoto(task.getId(), record.getPhotoId());
        if (count == null || count == 0) {
            throw new IllegalArgumentException("图片不属于该AI任务: " + record.getPhotoId());
        }
    }
}
