package com.hw.manage.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiTaskMetricsTest {
    @Test
    void recordsOnlyBoundedContractTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiTaskMetrics metrics = new AiTaskMetrics(registry);

        metrics.recordTransition("EMBEDDING_BACKFILL", "SUCCESS");
        metrics.recordTerminalDuration("EMBEDDING_BACKFILL", "SUCCESS", Duration.ofMillis(250));
        metrics.recordEmbeddingUpserts("clip-vit-l-14", "chroma", "READY", 3);

        assertEquals(1.0, registry.find("clip_ai_task_transitions_total")
                .tags("task_type", "EMBEDDING_BACKFILL", "status", "SUCCESS").counter().count());
        assertEquals(1L, registry.find("clip_ai_task_terminal_duration_seconds")
                .tags("task_type", "EMBEDDING_BACKFILL", "status", "SUCCESS").timer().count());
        assertEquals(3.0, registry.find("clip_embedding_records_upserted_total")
                .tags("embedding_model", "clip-vit-l-14", "vector_db", "chroma", "status", "READY").counter().count());
        assertFalse(registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey()).anyMatch(key -> key.equals("task_id") || key.equals("user_id")));
    }
}
