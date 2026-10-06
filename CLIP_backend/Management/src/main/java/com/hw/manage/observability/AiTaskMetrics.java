package com.hw.manage.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiTaskMetrics {
    private final MeterRegistry meterRegistry;

    public AiTaskMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordTransition(String taskType, String status) {
        Counter.builder("clip_ai_task_transitions_total")
                .tags("task_type", taskType, "status", status)
                .register(meterRegistry)
                .increment();
    }

    public void recordTerminalDuration(String taskType, String status, Duration duration) {
        Timer.builder("clip_ai_task_terminal_duration_seconds")
                .tags("task_type", taskType, "status", status)
                .register(meterRegistry)
                .record(duration);
    }

    public void recordEmbeddingUpserts(String embeddingModel, String vectorDb, String status, int count) {
        Counter.builder("clip_embedding_records_upserted_total")
                .tags("embedding_model", embeddingModel, "vector_db", vectorDb, "status", status)
                .register(meterRegistry)
                .increment(count);
    }
}
