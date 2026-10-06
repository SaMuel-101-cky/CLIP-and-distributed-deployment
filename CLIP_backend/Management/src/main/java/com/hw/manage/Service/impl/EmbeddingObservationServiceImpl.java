package com.hw.manage.Service.impl;

import com.github.pagehelper.PageHelper;
import com.hw.manage.Mapper.EmbeddingRecordMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.manage.Service.EmbeddingObservationService;
import com.hw.pojo.dto.EmbeddingRecordDto;
import com.hw.pojo.dto.EmbeddingRecordQueryDto;
import com.hw.pojo.entity.EmbeddingRecord;
import com.hw.pojo.entity.User;
import com.hw.pojo.query.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingObservationServiceImpl implements EmbeddingObservationService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserMapper userMapper;
    private final EmbeddingRecordMapper embeddingRecordMapper;

    @Override
    public PageResult<EmbeddingRecordDto> listRecords(String username, EmbeddingRecordQueryDto query) {
        EmbeddingRecordQueryDto normalized = normalize(query);
        User user = userMapper.findByUsername(username);
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        List<EmbeddingRecord> records;
        PageHelper.startPage(normalized.getPage(), normalized.getPageSize());
        try {
            records = embeddingRecordMapper.listByUserId(user.getId(), normalized.getStatus(), normalized.getEmbeddingModel());
        } finally {
            PageHelper.clearPage();
        }
        Long total = embeddingRecordMapper.countByUserId(user.getId(), normalized.getStatus(), normalized.getEmbeddingModel());
        return new PageResult<>(records.stream().map(this::toDto).toList(), total);
    }

    private EmbeddingRecordQueryDto normalize(EmbeddingRecordQueryDto query) {
        EmbeddingRecordQueryDto normalized = query == null ? new EmbeddingRecordQueryDto() : query;
        Integer page = normalized.getPage();
        Integer pageSize = normalized.getPageSize();
        if (page == null) {
            normalized.setPage(DEFAULT_PAGE);
        } else if (page < 1) {
            throw new IllegalArgumentException("page 必须大于等于 1");
        }
        if (pageSize == null) {
            normalized.setPageSize(DEFAULT_PAGE_SIZE);
        } else if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize 必须在 1 到 100 之间");
        }
        normalized.setStatus(normalizeFilter(normalized.getStatus()));
        normalized.setEmbeddingModel(normalizeFilter(normalized.getEmbeddingModel()));
        return normalized;
    }

    private String normalizeFilter(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private EmbeddingRecordDto toDto(EmbeddingRecord record) {
        EmbeddingRecordDto result = new EmbeddingRecordDto();
        result.setTargetId(record.getTargetId());
        result.setTargetType(record.getTargetType());
        result.setEmbeddingModel(record.getEmbeddingModel());
        result.setVectorDb(record.getVectorDb());
        result.setCollectionName(record.getCollectionName());
        result.setVectorId(record.getVectorId());
        result.setDim(record.getDim());
        result.setStatus(record.getStatus());
        result.setCreatedAt(record.getCreatedAt());
        result.setUpdatedAt(record.getUpdatedAt());
        return result;
    }
}
