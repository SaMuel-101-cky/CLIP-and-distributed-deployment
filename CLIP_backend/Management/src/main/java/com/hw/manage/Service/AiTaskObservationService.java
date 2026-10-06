package com.hw.manage.Service;

import com.hw.pojo.dto.AiTaskStatusDto;

public interface AiTaskObservationService {
    AiTaskStatusDto getTaskStatus(Long taskId, String username);
}
