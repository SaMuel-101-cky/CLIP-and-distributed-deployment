package com.hw.manage.Service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hw.manage.Mapper.*;
import com.hw.manage.Service.CategoryService;
import com.hw.manage.Service.storage.FileStorageService;
import com.hw.manage.Service.storage.StoredFile;
import com.hw.pojo.dto.AiUploadDto;
import com.hw.pojo.entity.AiTask;
import com.hw.pojo.entity.Description;
import com.hw.pojo.entity.Photos;
import com.hw.pojo.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.hw.pojo.query.Result;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final UserMapper userMapper;
    private final AiTaskMapper aiTaskMapper;
    private final PhotosMapper photosMapper;
    private final DescriptionMapper descriptionMapper;
    private final PhotoDescriptionMapper photoDescriptionMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;

    @Value("${ai.service.base-url:http://localhost:5000}")
    private String aiServiceBaseUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> categoryUpload(String username, String descriptionJson, MultipartFile[] photoList) throws Exception {
        log.info("业务处理 - 分类上传: User={}", username);

        // 1. 校验用户
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在: " + username);
        }
        Long userId = user.getId();

        // 2. 解析描述 JSON -> List
        List<String> descriptions;
        try {
            descriptions = objectMapper.readValue(descriptionJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("descriptionList 格式错误，必须为 JSON 数组字符串");
        }

        // 3. 存储图片并写入数据库
        List<String> photoPaths = new ArrayList<>();
        List<Long> photoIds = new ArrayList<>();

        AiTask task = new AiTask();
        task.setUserId(userId);
        task.setTaskType("CATEGORY");
        task.setStatus("PENDING");
        aiTaskMapper.insert(task);

        for (MultipartFile file : photoList) {
            StoredFile storedFile = fileStorageService.store(file);

            // 数据库记录
            Photos photo = new Photos();
            photo.setUserId(userId);
            photo.setStoragePath(storedFile.storagePath());
            photo.setOriginalName(storedFile.originalName());
            photo.setStoredName(storedFile.storedName());
            photo.setAccessUrl(storedFile.accessUrl());
            photo.setContentHash(storedFile.contentHash());
            photo.setMimeType(storedFile.mimeType());
            photo.setSizeBytes(storedFile.sizeBytes());
            photo.setStatus("ACTIVE");

            photosMapper.insert(photo);
            aiTaskMapper.addPhoto(task.getId(), photo.getId());

            photoIds.add(photo.getId());
            photoPaths.add(storedFile.storagePath());
        }

        // 4. 存储描述
        List<Long> descriptionIds = new ArrayList<>();
        for (String content : descriptions) {
            Description desc = new Description();
            desc.setContent(content);
            desc.setUserId(userId);
            desc.setTextType("CATEGORY_LABEL");

            descriptionMapper.insert(desc);
            aiTaskMapper.addDescription(task.getId(), desc.getId());
            descriptionIds.add(desc.getId());
        }

        // 5. 调用 AI 接口
        AiUploadDto uploadDto = new AiUploadDto();
        uploadDto.setPhotosList(photoPaths);
        uploadDto.setPhotosId(photoIds);
        uploadDto.setDescriptionsList(descriptions);
        uploadDto.setDescriptionsId(descriptionIds);
        uploadDto.setTaskId(task.getId());

        String url = aiServiceBaseUrl + "/upload";
        try {
            log.info("正在请求AI接口: {}", url);
            ResponseEntity<Result> response = restTemplate.postForEntity(url, uploadDto, Result.class);
            Result aiResult = response.getBody();

            // 修改处：判断逻辑改为 check result 是否为 null 以及 code 是否不等于 1 (1是成功)
            if (aiResult == null || aiResult.getCode() != 1) {
                String errorMsg = (aiResult != null && aiResult.getMessage() != null)
                        ? aiResult.getMessage()
                        : "空响应";
                throw new RuntimeException("AI端返回失败: " + errorMsg);
            }
            aiTaskMapper.updateStatus(task.getId(), "RUNNING", null);
        } catch (Exception e) {
            log.error("调用AI接口异常", e);
            aiTaskMapper.updateStatus(task.getId(), "FAILED", e.getMessage());
            throw new RuntimeException("连接AI服务失败: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("msg", "上传成功，AI正在处理中");
        return result;
    }

    @Override
    public Map<String, Object> categoryDownload(String username, Long taskId) throws Exception {
        // 下载逻辑保持不变
        log.info("业务处理 - 获取结果: User={}, taskId={}", username, taskId);

        User user = userMapper.findByUsername(username);
        if (user == null) throw new RuntimeException("用户不存在");
        Long userId = user.getId();

        AiTask task = aiTaskMapper.findByIdAndUserId(taskId, userId);
        if (task == null) {
            throw new RuntimeException("未找到该AI任务");
        }
        if ("FAILED".equals(task.getStatus())) {
            throw new RuntimeException("AI任务失败: " + task.getErrorMessage());
        }

        Integer count = photoDescriptionMapper.countMatchesByTask(taskId);
        if (count == 0) {
            throw new RuntimeException("AI正在处理中或未找到该批次数据，请稍后重试");
        }

        List<Description> descList = descriptionMapper.findByUserAndTaskId(userId, taskId);
        List<List<String>> resultData = new ArrayList<>();

        for (Description desc : descList) {
            List<Long> photoIds = photoDescriptionMapper.findPhotoIdsByTaskAndDescription(taskId, desc.getId());
            List<String> urls = new ArrayList<>();
            if (photoIds != null) {
                for (Long pid : photoIds) {
                    String path = photosMapper.findImagePathById(pid);
                    if (path != null) {
                        String url = fileStorageService.toAccessUrl(path);
                        if (url != null) urls.add(url);
                    }
                }
            }
            resultData.add(urls);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("result", resultData);
        return map;
    }
}
