package com.hw.manage.Service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface CategoryService {
    // 图片分类上传
    Map<String, Object> categoryUpload(String username, String descriptionJson, MultipartFile[] photoList) throws Exception;

    // 图片分类结果下载
    Map<String, Object> categoryDownload(String username, Long taskId) throws Exception;
}
