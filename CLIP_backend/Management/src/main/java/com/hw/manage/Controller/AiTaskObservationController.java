package com.hw.manage.Controller;

import com.hw.manage.Service.AiTaskObservationService;
import com.hw.pojo.query.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user/ai-tasks")
public class AiTaskObservationController {
    private final AiTaskObservationService aiTaskObservationService;

    @GetMapping("/{taskId}")
    public Result getTaskStatus(@PathVariable Long taskId, Authentication authentication) {
        return Result.success(aiTaskObservationService.getTaskStatus(taskId, authentication.getName()));
    }
}
