package com.hw.manage.Service;

import com.hw.pojo.dto.AiMatchResultDto;

public interface AiTaskService {
    void saveMatches(Long taskId, AiMatchResultDto result);
}
