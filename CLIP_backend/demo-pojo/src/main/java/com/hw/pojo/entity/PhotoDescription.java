package com.hw.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhotoDescription {
    private Long id;
    private Long taskId;
    private Long photoId;
    private Long descriptionId;
    private String matchType;
    private Double score;
    private Integer rankNo;
}
