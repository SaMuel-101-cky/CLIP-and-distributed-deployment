package com.hw.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Photos {
    private Integer albumsId;
    private String image;//文件地址
    private LocalDateTime createTime;
    private Integer descriptionId;
    private Integer userId;
    private String imageName;//文件的原始名字
    private String imageUuid;//文件的UUID名字
    private Integer idNum;//文件的序号id
    private Integer id;



}
