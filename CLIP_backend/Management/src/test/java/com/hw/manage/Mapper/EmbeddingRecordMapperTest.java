package com.hw.manage.Mapper;

import com.hw.pojo.entity.EmbeddingRecord;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingRecordMapperTest {

    @Test
    void upsertEmbeddingRecordUsesTargetAndModelAsIdempotencyKey() throws Exception {
        Method upsert = EmbeddingRecordMapper.class.getMethod("upsert", EmbeddingRecord.class);
        Insert insert = upsert.getAnnotation(Insert.class);

        assertNotNull(insert);
        String sql = String.join(" ", insert.value());
        assertTrue(sql.contains("embedding_records"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains("vector_id"));
        assertTrue(sql.contains("status"));

        assertNotNull(EmbeddingRecord.class.getDeclaredField("targetType"));
        assertNotNull(EmbeddingRecord.class.getDeclaredField("targetId"));
        assertNotNull(EmbeddingRecord.class.getDeclaredField("embeddingModel"));
        assertNotNull(EmbeddingRecord.class.getDeclaredField("vectorId"));
        assertNotNull(EmbeddingRecord.class.getDeclaredField("dim"));
        assertNotNull(EmbeddingRecord.class.getDeclaredField("status"));
    }
}
