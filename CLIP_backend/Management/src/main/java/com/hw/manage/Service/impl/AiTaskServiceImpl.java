package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.AiTaskMapper;
import com.hw.manage.Mapper.PhotoDescriptionMapper;
import com.hw.manage.Mapper.PhotosMapper;
import com.hw.manage.Service.AiTaskService;
import com.hw.pojo.dto.AiMatchDto;
import com.hw.pojo.dto.AiMatchResultDto;
import com.hw.pojo.entity.AiTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiTaskServiceImpl implements AiTaskService {
    private final AiTaskMapper aiTaskMapper;
    private final PhotoDescriptionMapper photoDescriptionMapper;
    private final PhotosMapper photosMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMatches(Long taskId, AiMatchResultDto result) {
        if (result.getTaskId() != null && !taskId.equals(result.getTaskId())) {
            throw new IllegalArgumentException("路径 taskId 与请求体 taskId 不一致");
        }

        AiTask task = aiTaskMapper.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("AI任务不存在: " + taskId);
        }

        if ("FAILED".equalsIgnoreCase(result.getStatus())) {
            aiTaskMapper.updateStatus(taskId, "FAILED", result.getErrorMessage());
            return;
        }

        if (CollectionUtils.isEmpty(result.getMatches())) {
            throw new IllegalArgumentException("matches 不能为空");
        }

        int rank = 1;
        for (AiMatchDto match : result.getMatches()) {
            validateMatch(task, match);
            String matchType = StringUtils.hasText(match.getMatchType())
                    ? match.getMatchType()
                    : task.getTaskType();
            Integer rankNo = match.getRankNo() != null ? match.getRankNo() : rank;
            photoDescriptionMapper.upsertMatch(
                    taskId,
                    match.getPhotoId(),
                    match.getDescriptionId(),
                    matchType,
                    match.getScore(),
                    rankNo
            );
            rank++;
        }

        aiTaskMapper.updateStatus(taskId, "SUCCESS", null);
    }

    private void validateMatch(AiTask task, AiMatchDto match) {
        if (match.getPhotoId() == null || match.getDescriptionId() == null) {
            throw new IllegalArgumentException("photoId 和 descriptionId 不能为空");
        }
        if ("CATEGORY".equals(task.getTaskType())) {
            if (photoDescriptionMapper.countTaskPhoto(task.getId(), match.getPhotoId()) == 0) {
                throw new IllegalArgumentException("图片不属于该AI任务: " + match.getPhotoId());
            }
        } else if ("TEXT_SEARCH".equals(task.getTaskType())) {
            if (photosMapper.countActiveByUserAndId(task.getUserId(), match.getPhotoId()) == 0) {
                throw new IllegalArgumentException("图片不属于该用户或已删除: " + match.getPhotoId());
            }
        }
        if (photoDescriptionMapper.countTaskDescription(task.getId(), match.getDescriptionId()) == 0) {
            throw new IllegalArgumentException("描述不属于该AI任务: " + match.getDescriptionId());
        }
    }
}
