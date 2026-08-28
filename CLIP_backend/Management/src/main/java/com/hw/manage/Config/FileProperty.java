package com.hw.manage.Config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperty {
    /**
     * 文件访问URL前缀（用于前端访问）
     */

    @Value("${file.access-path}")
    private String accessPath;

    /**
     * 文件上传目录（相对路径）
     */
    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * 图片子目录
     */
    private String imageDir = "images/";

    /**
     * 最大文件大小（字节）
     */
    private long maxFileSize = 5 * 1024 * 1024;

    /**
     * 允许的图片类型
     */
    private String[] allowedImageTypes = {"jpg", "jpeg", "png", "gif", "bmp", "webp"};
}