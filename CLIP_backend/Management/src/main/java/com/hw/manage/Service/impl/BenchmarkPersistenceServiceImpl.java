package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.BenchmarkMapper;
import com.hw.pojo.entity.BenchmarkRun;
import com.hw.pojo.entity.BenchmarkSample;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class BenchmarkPersistenceServiceImpl {
    private static final Set<String> ERROR_TYPES = Set.of("REMOTE_TIMEOUT", "REMOTE_UNAVAILABLE", "UNAUTHORIZED", "INVALID_TENSOR", "REMOTE_5XX", "CANCELLED");
    private final BenchmarkMapper benchmarkMapper;

    public void createRun(BenchmarkRun run) {
        if (run == null || run.getRunId() == null || run.getRunId().isBlank() || run.getResolvedPlanJson() == null) {
            throw new IllegalArgumentException("runId and resolved plan are required");
        }
        benchmarkMapper.insertRun(run);
    }

    public void updateRun(String runId, String status, String errorType) {
        if (errorType != null && !isBoundedErrorType(errorType)) {
            throw new IllegalArgumentException("unsupported error type");
        }
        benchmarkMapper.updateRun(runId, status, errorType);
    }

    public boolean isBoundedErrorType(String errorType) {
        return ERROR_TYPES.contains(errorType);
    }

    public void recordSample(String runId, BenchmarkSample sample) {
        if (sample == null || !runId.equals(sample.getRunId())) {
            throw new IllegalArgumentException("sample runId does not match path");
        }
        if (sample.getErrorType() != null && !isBoundedErrorType(sample.getErrorType())) {
            throw new IllegalArgumentException("unsupported error type");
        }
        benchmarkMapper.insertSample(sample);
    }
}
