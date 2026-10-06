package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.AiTaskMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.pojo.dto.AiTaskStatusDto;
import com.hw.pojo.entity.AiTask;
import com.hw.pojo.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTaskObservationServiceImplTest {
    @Mock private AiTaskMapper aiTaskMapper;
    @Mock private UserMapper userMapper;

    @Test
    void getTaskStatusReturnsOnlyTheCurrentUsersTask() {
        User alice = new User();
        alice.setId(7L);
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 6, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 6, 10, 1);
        AiTask task = new AiTask(31L, 7L, "EMBEDDING_BACKFILL", "SUCCESS", null, createdAt, updatedAt);
        when(userMapper.findByUsername("alice")).thenReturn(alice);
        when(aiTaskMapper.findByIdAndUserId(31L, 7L)).thenReturn(task);
        when(aiTaskMapper.countTaskPhotos(31L)).thenReturn(2);

        AiTaskStatusDto result = service().getTaskStatus(31L, "alice");

        assertEquals(31L, result.getTaskId());
        assertEquals("EMBEDDING_BACKFILL", result.getTaskType());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(null, result.getErrorMessage());
        assertEquals(2, result.getPhotoCount());
        assertEquals(createdAt, result.getCreatedAt());
        assertEquals(updatedAt, result.getUpdatedAt());
        verify(aiTaskMapper, never()).findById(31L);
    }

    @Test
    void getTaskStatusRejectsMissingOrForeignTask() {
        when(userMapper.findByUsername("missing")).thenReturn(null);
        when(userMapper.findByUsername("alice")).thenReturn(user(7L));
        when(aiTaskMapper.findByIdAndUserId(31L, 7L)).thenReturn(null);

        IllegalArgumentException missingUser = assertThrows(IllegalArgumentException.class,
                () -> service().getTaskStatus(31L, "missing"));
        IllegalArgumentException foreignTask = assertThrows(IllegalArgumentException.class,
                () -> service().getTaskStatus(31L, "alice"));

        assertEquals("AI任务不存在或无权访问", missingUser.getMessage());
        assertEquals("AI任务不存在或无权访问", foreignTask.getMessage());
        verify(aiTaskMapper, never()).findById(31L);
    }

    private AiTaskObservationServiceImpl service() {
        return new AiTaskObservationServiceImpl(aiTaskMapper, userMapper);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
