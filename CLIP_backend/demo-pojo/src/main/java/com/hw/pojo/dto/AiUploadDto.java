package com.hw.pojo.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiUploadDto {
    private Long userId;                   // 用户ID
    private List<String> photosList;       // 图片路径列表
    private List<Long> photosId;           // 图片ID列表
    private List<String> descriptionsList; // 描述文本列表
    private List<Long> descriptionsId;     // 描述ID列表
    private Long taskId;                   // AI任务ID
}
