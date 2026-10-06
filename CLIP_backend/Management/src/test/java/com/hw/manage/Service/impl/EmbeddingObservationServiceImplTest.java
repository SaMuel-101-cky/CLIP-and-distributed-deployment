package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.EmbeddingRecordMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.pojo.dto.EmbeddingRecordDto;
import com.hw.pojo.dto.EmbeddingRecordQueryDto;
import com.hw.pojo.entity.EmbeddingRecord;
import com.hw.pojo.entity.User;
import com.hw.pojo.query.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingObservationServiceImplTest {
    @Mock private UserMapper userMapper;
    @Mock private EmbeddingRecordMapper embeddingRecordMapper;

    @Test
    void listRecordsAppliesCurrentUserAndOptionalFilters() {
        when(userMapper.findByUsername("alice")).thenReturn(user(7L));
        when(embeddingRecordMapper.listByUserId(7L, "READY", "clip-vit-l-14"))
                .thenReturn(List.of(record()));
        when(embeddingRecordMapper.countByUserId(7L, "READY", "clip-vit-l-14")).thenReturn(1L);
        EmbeddingRecordQueryDto query = new EmbeddingRecordQueryDto();
        query.setStatus("READY");
        query.setEmbeddingModel("clip-vit-l-14");

        PageResult<EmbeddingRecordDto> result = service().listRecords("alice", query);

        verify(embeddingRecordMapper).listByUserId(7L, "READY", "clip-vit-l-14");
        verify(embeddingRecordMapper).countByUserId(7L, "READY", "clip-vit-l-14");
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        EmbeddingRecordDto dto = result.getList().getFirst();
        assertEquals(11L, dto.getTargetId());
        assertEquals("IMAGE", dto.getTargetType());
        assertEquals("clip-vit-l-14", dto.getEmbeddingModel());
        assertFalse(List.of(EmbeddingRecordDto.class.getDeclaredFields())
                .stream().anyMatch(field -> field.getName().equals("userId")));
        assertEquals(1, query.getPage());
        assertEquals(20, query.getPageSize());
    }

    @Test
    void listRecordsRejectsInvalidPagination() {
        EmbeddingRecordQueryDto pageZero = new EmbeddingRecordQueryDto();
        pageZero.setPage(0);
        EmbeddingRecordQueryDto sizeZero = new EmbeddingRecordQueryDto();
        sizeZero.setPageSize(0);
        EmbeddingRecordQueryDto tooLarge = new EmbeddingRecordQueryDto();
        tooLarge.setPageSize(101);

        assertThrows(IllegalArgumentException.class, () -> service().listRecords("alice", pageZero));
        assertThrows(IllegalArgumentException.class, () -> service().listRecords("alice", sizeZero));
        assertThrows(IllegalArgumentException.class, () -> service().listRecords("alice", tooLarge));
    }

    private EmbeddingObservationServiceImpl service() {
        return new EmbeddingObservationServiceImpl(userMapper, embeddingRecordMapper);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private EmbeddingRecord record() {
        return new EmbeddingRecord(1L, 7L, "IMAGE", 11L, "clip-vit-l-14", "chroma",
                "clip_image_embeddings", "vector-11", 768, "READY",
                LocalDateTime.of(2026, 10, 6, 10, 0), LocalDateTime.of(2026, 10, 6, 10, 1));
    }
}
