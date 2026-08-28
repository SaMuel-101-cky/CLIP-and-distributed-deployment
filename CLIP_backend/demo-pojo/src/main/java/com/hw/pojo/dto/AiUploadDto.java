package com.hw.pojo.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiUploadDto {
    private List<String> photosList;       // 图片路径列表
    private List<Integer> photosId;        // 图片ID列表
    private List<String> descriptionsList; // 描述文本列表
    private List<Integer> descriptionsId;  // 描述ID列表
}