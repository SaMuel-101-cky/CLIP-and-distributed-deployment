package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.AiTaskMapper;
import com.hw.manage.Mapper.PhotosMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.manage.Service.EmbeddingBackfillService;
import com.hw.pojo.dto.AiEmbeddingBackfillDto;
import com.hw.pojo.dto.EmbeddingBackfillRequestDto;
import com.hw.pojo.entity.AiTask;
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

    @Value("${ai.service.base-url:http://localhost:5000}")
    private String aiServiceBaseUrl;

    @Value("${ai.embedding.model:clip-vit-l-14}")
    private String embeddingModel;

    @Value("${ai.vector.db:chroma}")
    private String vectorDb;

    @Value("${ai.vector.collection:clip_image_embeddings}")
    private String collectionName;

    @Override
    @Transactional(rollbackFor = Exception.class)
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
}
