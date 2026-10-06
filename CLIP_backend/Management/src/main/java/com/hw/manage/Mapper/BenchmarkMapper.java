package com.hw.manage.Mapper;

import com.hw.pojo.entity.BenchmarkRun;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.hw.pojo.entity.BenchmarkSample;

@Mapper
public interface BenchmarkMapper {
    @Insert("INSERT INTO benchmark_runs(run_id, status, resolved_plan_json) VALUES(#{runId}, #{status}, #{resolvedPlanJson})")
    void insertRun(BenchmarkRun run);

    @Update("UPDATE benchmark_runs SET status=#{status}, error_type=#{errorType}, completed_at=CURRENT_TIMESTAMP WHERE run_id=#{runId}")
    void updateRun(@Param("runId") String runId, @Param("status") String status, @Param("errorType") String errorType);

    @Insert("INSERT INTO benchmark_samples(run_id, sequence_no, is_warmup, total_ms, success, error_type) VALUES(#{runId}, #{sequenceNo}, #{warmup}, #{totalMs}, #{success}, #{errorType})")
    void insertSample(BenchmarkSample sample);
}
