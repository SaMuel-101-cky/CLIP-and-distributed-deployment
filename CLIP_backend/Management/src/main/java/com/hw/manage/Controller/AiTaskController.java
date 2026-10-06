package com.hw.manage.Controller;

import com.hw.manage.Service.AiTaskService;
import com.hw.manage.Service.EmbeddingBackfillService;
import com.hw.pojo.dto.AiEmbeddingResultDto;
import com.hw.pojo.dto.AiMatchResultDto;
import com.hw.pojo.query.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/tasks")
public class AiTaskController {
    private final AiTaskService aiTaskService;
    private final EmbeddingBackfillService embeddingBackfillService;

    @Value("${ai.callback.token:}")
    private String aiCallbackToken;

    @PostMapping("/{taskId}/matches")
    public Result saveMatches(@PathVariable Long taskId,
                              @RequestBody AiMatchResultDto result,
                              @RequestHeader(value = "X-AI-Callback-Token", required = false) String callbackToken) {
        try {
            validateCallbackToken(callbackToken);
            aiTaskService.saveMatches(taskId, result);
            return Result.success();
        } catch (Exception e) {
            log.error("保存AI任务结果失败，taskId={}", taskId, e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{taskId}/embeddings")
    public Result saveEmbeddings(@PathVariable Long taskId,
                                 @RequestBody AiEmbeddingResultDto result,
                                 @RequestHeader(value = "X-AI-Callback-Token", required = false) String callbackToken) {
        try {
            validateCallbackToken(callbackToken);
            embeddingBackfillService.saveEmbeddingResult(taskId, result);
            return Result.success();
        } catch (Exception e) {
            log.error("保存 embedding 回调失败，taskId={}", taskId, e);
            return Result.error(e.getMessage());
        }
    }

    private void validateCallbackToken(String callbackToken) {
        if (StringUtils.hasText(aiCallbackToken) && !aiCallbackToken.equals(callbackToken)) {
            throw new IllegalArgumentException("AI回调Token无效");
        }
    }
}
