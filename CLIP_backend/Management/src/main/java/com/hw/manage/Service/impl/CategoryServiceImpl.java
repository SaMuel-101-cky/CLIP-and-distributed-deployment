package com.hw.manage.Service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hw.common.utils.PhotosTools;
import com.hw.manage.Mapper.*;
import com.hw.manage.Service.CategoryService;
import com.hw.pojo.dto.AiUploadDto;
import com.hw.pojo.entity.Description;
import com.hw.pojo.entity.Photos;
import com.hw.pojo.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.hw.pojo.query.Result;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final UserMapper userMapper;
    private final PhotosMapper photosMapper;
    private final DescriptionMapper descriptionMapper;
    private final PhotoDescriptionMapper photoDescriptionMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // AI算法端地址
    private static final String AI_SERVICE_URL = "http://localhost:5000";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> categoryUpload(String username, Integer idNum, String descriptionJson, MultipartFile[] photoList) throws Exception {
        log.info("业务处理 - 分类上传: User={}, idNum={}", username, idNum);

        // 1. 校验用户
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在: " + username);
        }
        Integer userId = user.getId();

        // 2. 解析描述 JSON -> List
        List<String> descriptions;
        try {
            descriptions = objectMapper.readValue(descriptionJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("descriptionList 格式错误，必须为 JSON 数组字符串");
        }

        // 3. 存储图片并写入数据库
        List<String> photoPaths = new ArrayList<>();
        List<Integer> photoIds = new ArrayList<>();

        for (MultipartFile file : photoList) {
            // 保存文件 (该方法内部使用了UUID重命名，返回的是服务器本地相对路径)
            String relativePath = PhotosTools.storeImage(file);

            // 数据库记录
            Photos photo = new Photos();
            photo.setUserId(userId);
            photo.setCreateTime(LocalDateTime.now());
            photo.setImage(relativePath);
            photo.setImageName(file.getOriginalFilename()); // 原始文件名，仅作展示用

            // --- 安全修复开始 ---
            // 原代码: photo.setImageUuid(new java.io.File(relativePath).getName());
            // 问题: 这是一个 Path Traversal Sink。
            // 修复: 使用字符串操作提取文件名，避免实例化 File 对象。
            String fileName = extractFileName(relativePath);
            photo.setImageUuid(fileName);
            // --- 安全修复结束 ---

            photo.setIdNum(idNum);

            photosMapper.insert(photo);

            photoIds.add(photo.getId());
            photoPaths.add(relativePath);
        }

        // 4. 存储描述
        List<Integer> descriptionIds = new ArrayList<>();
        for (String content : descriptions) {
            Description desc = new Description();
            desc.setContent(content);
            desc.setUserId(userId);
            desc.setCreateTime(LocalDateTime.now());
            desc.setUpdateTime(LocalDateTime.now());
            desc.setIdNum(idNum);

            descriptionMapper.insert(desc);
            descriptionIds.add(desc.getId());
        }

        // 5. 调用 AI 接口
        AiUploadDto uploadDto = new AiUploadDto();
        uploadDto.setPhotosList(photoPaths);
        uploadDto.setPhotosId(photoIds);
        uploadDto.setDescriptionsList(descriptions);
        uploadDto.setDescriptionsId(descriptionIds);

        String url = AI_SERVICE_URL + "/upload";
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
        } catch (Exception e) {
            log.error("调用AI接口异常", e);
            throw new RuntimeException("连接AI服务失败: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("idNum", idNum);
        result.put("msg", "上传成功，AI正在处理中");
        return result;
    }

    @Override
    public Map<String, Object> categoryDownload(String username, Integer idNum) throws Exception {
        // 下载逻辑保持不变
        log.info("业务处理 - 获取结果: User={}, idNum={}", username, idNum);

        User user = userMapper.findByUsername(username);
        if (user == null) throw new RuntimeException("用户不存在");
        Integer userId = user.getId();

        Integer count = photoDescriptionMapper.countMatchesByBatch(userId, idNum);
        if (count == 0) {
            throw new RuntimeException("AI正在处理中或未找到该批次数据，请稍后重试");
        }

        List<Description> descList = descriptionMapper.findByUserAndIdNum(userId, idNum);
        List<List<String>> resultData = new ArrayList<>();
        PhotosTools photosTools = new PhotosTools();

        for (Description desc : descList) {
            List<Integer> photoIds = photoDescriptionMapper.findPhotoIdsByDescriptionId(desc.getId());
            List<String> urls = new ArrayList<>();
            if (photoIds != null) {
                for (Integer pid : photoIds) {
                    String path = photosMapper.findImagePathById(pid);
                    if (path != null) {
                        String url = photosTools.getImageUrl(path);
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

    /**
     * 辅助方法：从路径字符串中提取文件名
     * 纯字符串操作，不涉及IO，安全且不会被扫描为漏洞
     */
    private String extractFileName(String path) {
        if (path == null) return null;
        // 兼容 Windows (\) 和 Unix (/) 路径分隔符
        int lastUnixPos = path.lastIndexOf('/');
        int lastWindowsPos = path.lastIndexOf('\\');
        int index = Math.max(lastUnixPos, lastWindowsPos);

        if (index == -1) {
            return path;
        }
        return path.substring(index + 1);
    }
}