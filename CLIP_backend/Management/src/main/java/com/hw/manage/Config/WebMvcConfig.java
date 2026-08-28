package com.hw.manage.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 静态资源映射配置
 */
@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private FileProperty fileProperty;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将URL路径映射到本地文件目录
        String uploadDir = fileProperty.getUploadDir();

        // 确保目录存在
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 配置静态资源映射
        registry.addResourceHandler(fileProperty.getAccessPath() + "**")
                .addResourceLocations("file:" + uploadDir + File.separator);

        log.info("配置静态资源映射: {} -> file:{}",
                fileProperty.getAccessPath(), uploadDir);
    }
}