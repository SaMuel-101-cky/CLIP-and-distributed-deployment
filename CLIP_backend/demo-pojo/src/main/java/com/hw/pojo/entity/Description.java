package com.hw.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Description {
    private String content;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer userId;
    private Integer id;//主键Id
    private Integer idNum;//描述IdidNum

}
