package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.AiTaskMapper;
import com.hw.manage.Mapper.DescriptionMapper;
import com.hw.manage.Mapper.PhotoDescriptionMapper;
import com.hw.manage.Mapper.PhotosMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.manage.Service.UserService;
import com.hw.manage.Service.storage.FileStorageService;
import com.hw.pojo.dto.AiTextSearchDto;
import com.hw.pojo.dto.Descriptiondto;
import com.hw.pojo.entity.AiTask;
import com.hw.pojo.entity.Description;
import com.hw.pojo.entity.Photos;
import com.hw.pojo.entity.User;
import com.hw.pojo.query.Sequery;
import com.hw.pojo.query.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Data
public class UserServiceImpl1 implements UserService {
    //对密码进行加密存储
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AiTaskMapper aiTaskMapper;
    private final DescriptionMapper descriptionMapper;
    private final PhotoDescriptionMapper photoDescriptionMapper;
    private final PhotosMapper photosMapper;
    private final FileStorageService fileStorageService;
    private final RestTemplate restTemplate;

    @Value("${ai.service.base-url:http://localhost:5000}")
    private String aiServiceBaseUrl;

    @Transactional(propagation= Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    @Override
    public void changeuserInfo(User userInfo) {
        //逻辑是先删除数据库原本的数据然后将修改后的数据添加上去
        String name = userInfo.getUsername();
        log.info("修改用户的账户名：{}", name);
        LocalDateTime createTime = userMapper.findByUsername(name).getCreateTime();//保留创造时间属性
        userMapper.delete(name);
        log.info("删除用户账户名：{}", name);
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfo.setCreateTime(createTime);
        String encodedPassword = passwordEncoder.encode(userInfo.getPassword());//对密码进行加密存储
        userInfo.setPassword(encodedPassword);
        userMapper.add(userInfo);
        log.info("添加用户账户名：{}", name);
        log.info("修改用户账户名成功");
    }
    @Override
    @Transactional(propagation= Propagation.REQUIRES_NEW, rollbackFor=Exception.class)
    public Long uploadmatch(Descriptiondto descriptiondto)
    {
        log.info("上传文本搜索任务");
        User user = userMapper.findByUsername(descriptiondto.getUsername());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + descriptiondto.getUsername());
        }

        AiTask task = new AiTask();
        task.setUserId(user.getId());
        task.setTaskType("TEXT_SEARCH");
        task.setStatus("PENDING");
        aiTaskMapper.insert(task);

        Description description = new Description();
        description.setUserId(user.getId());
        description.setContent(descriptiondto.getDescription());
        description.setTextType("SEARCH_QUERY");
        descriptionMapper.insert(description);
        aiTaskMapper.addDescription(task.getId(), description.getId());

        List<Photos> activePhotos = photosMapper.listActivePhotoRecords(user.getId());
        if (activePhotos.isEmpty()) {
            aiTaskMapper.updateStatus(task.getId(), "FAILED", "该用户没有可搜索的图片");
            throw new RuntimeException("该用户没有可搜索的图片");
        }

        AiTextSearchDto aiRequest = new AiTextSearchDto();
        aiRequest.setTaskId(task.getId());
        aiRequest.setUserId(user.getId());
        aiRequest.setDescription(description.getContent());
        aiRequest.setDescriptionId(description.getId());
        aiRequest.setPhotosId(activePhotos.stream().map(Photos::getId).toList());
        aiRequest.setPhotosList(activePhotos.stream().map(Photos::getStoragePath).toList());

        String url = aiServiceBaseUrl + "/getPhotos";
        try {
            ResponseEntity<Result> response = restTemplate.postForEntity(url, aiRequest, Result.class);
            Result aiResult = response.getBody();
            if (aiResult == null || aiResult.getCode() != 1) {
                String errorMsg = (aiResult != null && aiResult.getMessage() != null)
                        ? aiResult.getMessage()
                        : "空响应";
                throw new RuntimeException("AI端返回失败: " + errorMsg);
            }
            aiTaskMapper.updateStatus(task.getId(), "RUNNING", null);
        } catch (Exception e) {
            aiTaskMapper.updateStatus(task.getId(), "FAILED", e.getMessage());
            throw new RuntimeException("连接AI服务失败: " + e.getMessage());
        }

        log.info("上传文本搜索任务成功，taskId={}", task.getId());
        return task.getId();
    }

    @Override
    public List<String> downloadmatch(Sequery sequery)throws Exception
    {
        log.info("开始下载文本搜索结果，taskId={}", sequery.getTaskId());
        User user = userMapper.findByUsername(sequery.getUsername());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + sequery.getUsername());
        }
        AiTask task = aiTaskMapper.findByIdAndUserId(sequery.getTaskId(), user.getId());
        if (task == null) {
            throw new Exception("未找到该AI任务");
        }
        if ("FAILED".equals(task.getStatus())) {
            throw new Exception("AI任务失败: " + task.getErrorMessage());
        }

        List<Description> descriptions = descriptionMapper.findByUserAndTaskId(user.getId(), task.getId());
        if (descriptions.isEmpty()) {
            throw new Exception("任务缺少查询文本");
        }
        Integer count = photoDescriptionMapper.countMatchesByTask(task.getId());
        if (count == 0) {
            throw new Exception("结果还没处理好");
        }

        List<String> urls = new ArrayList<>();
        for (Long photoId : photoDescriptionMapper.findPhotoIdsByTaskAndDescription(task.getId(), descriptions.get(0).getId())) {
            String storagePath = photosMapper.findImagePathById(photoId);
            String accessUrl = fileStorageService.toAccessUrl(storagePath);
            if (accessUrl != null) {
                urls.add(accessUrl);
            }
        }
        return urls;
    }

}
