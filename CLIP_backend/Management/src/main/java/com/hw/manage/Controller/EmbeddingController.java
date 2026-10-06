package com.hw.manage.Controller;

import com.hw.manage.Service.EmbeddingBackfillService;
import com.hw.manage.Service.EmbeddingObservationService;
import com.hw.pojo.dto.EmbeddingBackfillRequestDto;
import com.hw.pojo.dto.EmbeddingRecordQueryDto;
import com.hw.pojo.query.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/embeddings")
public class EmbeddingController {
    private final EmbeddingBackfillService embeddingBackfillService;
    private final EmbeddingObservationService embeddingObservationService;

    @GetMapping
    public Result listRecords(@ModelAttribute EmbeddingRecordQueryDto query, Authentication authentication) {
        return Result.success(embeddingObservationService.listRecords(authentication.getName(), query));
    }

    @PostMapping("/backfill")
    public Result backfill(@RequestBody EmbeddingBackfillRequestDto request) {
        try {
            return Result.success(embeddingBackfillService.startBackfill(request));
        } catch (Exception e) {
            log.error("创建 embedding backfill 任务失败", e);
            return Result.error(e.getMessage());
        }
    }
}
