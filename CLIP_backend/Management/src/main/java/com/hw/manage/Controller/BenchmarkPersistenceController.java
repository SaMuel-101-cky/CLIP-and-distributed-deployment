package com.hw.manage.Controller;

import com.hw.manage.Service.impl.BenchmarkPersistenceServiceImpl;
import com.hw.pojo.entity.BenchmarkRun;
import com.hw.pojo.entity.BenchmarkSample;
import com.hw.pojo.query.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Loopback benchmark worker persistence boundary; it never exposes DB credentials. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/benchmark/runs")
public class BenchmarkPersistenceController {
    private final BenchmarkPersistenceServiceImpl service;

    @PostMapping
    public Result create(@RequestBody BenchmarkRun run) {
        service.createRun(run);
        return Result.success(Map.of("runId", run.getRunId()));
    }

    @PatchMapping("/{runId}")
    public Result update(@PathVariable String runId, @RequestBody Map<String, String> body) {
        service.updateRun(runId, body.get("status"), body.get("errorType"));
        return Result.success();
    }

    @PostMapping("/{runId}/samples")
    public Result sample(@PathVariable String runId, @RequestBody BenchmarkSample sample) {
        service.recordSample(runId, sample);
        return Result.success();
    }
}
