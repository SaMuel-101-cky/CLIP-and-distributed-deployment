package com.hw.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * 图片存储工具类
 */

public class PhotosTools {

//此处存在本地了，后续要放服务器上的话，得换地址
    // 默认存储路径（相对路径）
    private static final String DEFAULT_BASE_DIR = "D:/web/uploads/images";
    private static final String DEFAULT_ACCESS_PATH = "/images/";
    // 允许的图片类型
    private static final String[] ALLOWED_EXTENSIONS = {
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    };

    // 最大文件大小 5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * 存储图片文件
     * @param file 图片文件
     * @return 存储的相对路径
     * @throws IOException 文件操作异常
     */
    public static String storeImage(MultipartFile file) throws IOException {
        return storeImage(file, DEFAULT_BASE_DIR);
    }

    /**
     * 存储图片文件到指定相对路径
     * @param file 图片文件
     * @param baseDir 基础目录
     * @return 存储的相对路径
     * @throws IOException 文件操作异常
     */
    public static String storeImage(MultipartFile file, String baseDir) throws IOException {
        // 参数校验
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 文件大小校验
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过5MB");
        }

        // 文件类型校验
        if (!isImageFile(file)) {
            throw new IllegalArgumentException("不支持的文件类型");
        }

        // 获取文件扩展名
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);

        // 生成唯一文件名
        String uniqueFileName = generateUniqueFileName(fileExtension);

        // 构建存储路径
        String relativePath = buildRelativePath(baseDir, uniqueFileName);
        Path fullPath = Paths.get(relativePath);

        // 创建目录
        createDirectories(fullPath.getParent());

        // 存储文件
        Files.copy(file.getInputStream(), fullPath);


        return relativePath;
    }

    public String getImageUrl(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return null;
        }

        //  清理并提取文件名
        // 使用 java.io.File 类的 getName() 方法来可靠地提取路径末尾的文件名，
        // 无论是使用正斜杠 (/) 还是反斜杠 (\)，都能正确处理。
        String filename = new File(relativePath).getName();

        //  确保文件名不为空（getName() 在某些情况下可能返回空字符串）
        if (filename.isEmpty()) {
            return null;
        }

        //  拼接：确保 accessPath 以 / 结尾，而 filename 不以 / 开头（它永远不会）
        // 这里假设 accessPath 已经处理为末尾带斜杠（如配置建议的 /images/）。
        // 如果不确定，可以改为：
        // String normalizedAccessPath = accessPath.endsWith("/") ? accessPath : accessPath + "/";
        // return normalizedAccessPath + filename;

        return DEFAULT_ACCESS_PATH + filename;
    }


    /**
     * 根据访问URL解析相对路径
     */
    public static String parseRelativePath(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return null;
        }
        String cleanUrl = imageUrl.trim();
        // 1. 检查 URL 是否以 accessPath 开头
       String accessPath = DEFAULT_ACCESS_PATH;
        if (cleanUrl.startsWith(accessPath)) {
            // 2. 移除 accessPath 部分
            String relativePath = cleanUrl.substring(accessPath.length());

            // 3. 再次检查相对路径是否为空，以防 imageUrl 只是 /images/
            if (relativePath.isEmpty()) {
                return null;
            }

            return DEFAULT_BASE_DIR+relativePath;
        }

        // 如果 URL 不以配置的前缀开头，则认为它不是一个有效的应用内部文件 URL
        return null;

    }

    /**
     * 删除图片文件
     * @param relativePath 相对路径
     * @return 是否删除成功
     */
    public static boolean deleteImage(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return false;
        }

        try {
            Path fullPath = Paths.get(relativePath);
            boolean deleted = Files.deleteIfExists(fullPath);
            if (deleted) {

            } else {

            }
            return deleted;
        } catch (IOException e) {

            return false;
        }
    }

    /**
     * 检查文件是否为图片
     */
    private static boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return false;
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return false;
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        for (String allowedExt : ALLOWED_EXTENSIONS) {
            if (allowedExt.equals(extension)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取文件扩展名
     */
    private static String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * 生成唯一文件名
     */
    private static String generateUniqueFileName(String extension) {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return timestamp + "_" + uuid + "." + extension.toLowerCase();
    }

    /**
     * 构建相对路径（按日期分目录）
     */
    private static String buildRelativePath(String baseDir, String filename) {
        return baseDir +  "/" + filename;
    }


    /**
     * 创建目录
     */
    private static void createDirectories(Path directoryPath) throws IOException {
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }
    }

    /**
     * 检查图片文件是否存在
     */
    public static boolean exists(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return false;
        }
        Path fullPath = Paths.get(relativePath);
        return Files.exists(fullPath);
    }

    /**
     * 获取文件大小
     */
    public static long getFileSize(String relativePath) throws IOException {
        if (!exists(relativePath)) {
            return 0;
        }
        Path fullPath = Paths.get(relativePath);
        return Files.size(fullPath);
    }
}