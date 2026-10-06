package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.AiTaskMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.manage.Service.AiTaskObservationService;
import com.hw.pojo.dto.AiTaskStatusDto;
import com.hw.pojo.entity.AiTask;
import com.hw.pojo.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiTaskObservationServiceImpl implements AiTaskObservationService {
    private static final String NOT_FOUND_OR_FORBIDDEN = "AI任务不存在或无权访问";

    private final AiTaskMapper aiTaskMapper;
    private final UserMapper userMapper;

    @Override
    public AiTaskStatusDto getTaskStatus(Long taskId, String username) {
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException(NOT_FOUND_OR_FORBIDDEN);
        }
        User user = userMapper.findByUsername(username);
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(NOT_FOUND_OR_FORBIDDEN);
        }
        AiTask task = aiTaskMapper.findByIdAndUserId(taskId, user.getId());
        if (task == null) {
            throw new IllegalArgumentException(NOT_FOUND_OR_FORBIDDEN);
        }

        AiTaskStatusDto result = new AiTaskStatusDto();
        result.setTaskId(task.getId());
        result.setTaskType(task.getTaskType());
        result.setStatus(task.getStatus());
        result.setErrorMessage(task.getErrorMessage());
        result.setPhotoCount(aiTaskMapper.countTaskPhotos(taskId));
        result.setCreatedAt(task.getCreatedAt());
        result.setUpdatedAt(task.getUpdatedAt());
        return result;
    }
}
