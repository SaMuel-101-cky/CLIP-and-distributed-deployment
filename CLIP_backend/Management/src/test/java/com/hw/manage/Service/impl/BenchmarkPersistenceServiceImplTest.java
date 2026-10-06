package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.BenchmarkMapper;
import com.hw.pojo.entity.BenchmarkRun;
import com.hw.pojo.entity.BenchmarkSample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BenchmarkPersistenceServiceImplTest {
    @Mock private BenchmarkMapper benchmarkMapper;

    @Test
    void createRunStoresResolvedPlanAndRejectsRawErrors() {
        BenchmarkRun run = new BenchmarkRun();
        run.setRunId("run-1");
        run.setStatus("PENDING");
        run.setResolvedPlanJson("{\"mode\":\"LOCAL_ALL\"}");

        BenchmarkPersistenceServiceImpl service = new BenchmarkPersistenceServiceImpl(benchmarkMapper);
        service.createRun(run);

        verify(benchmarkMapper).insertRun(run);
        assertThrows(IllegalArgumentException.class, () -> service.updateRun("run-1", "FAILED", "stack trace: secret"));
        assertTrue(service.isBoundedErrorType("REMOTE_TIMEOUT"));
    }

    @Test
    void recordSampleRejectsRunIdMismatch() {
        BenchmarkSample sample = new BenchmarkSample();
        sample.setRunId("other-run");
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkPersistenceServiceImpl(benchmarkMapper)
                .recordSample("run-1", sample));
    }
}
