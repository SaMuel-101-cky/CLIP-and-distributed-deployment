package com.hw.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhotoDescription {
    private Integer id;
    private Integer photoId;
    private Integer descriptionId;
    private Integer functionType;
}