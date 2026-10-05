package com.hw.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Description {
    private Long id;//主键Id
    private Long userId;
    private String content;
    private String textType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
