package com.hw.manage.Service.storage;

import com.hw.manage.Config.FileProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {
    private final FileProperty fileProperty;

    public LocalFileStorageService(FileProperty fileProperty) {
        this.fileProperty = fileProperty;
    }

    @Override
    public StoredFile store(MultipartFile file) throws IOException {
        validate(file);

        String originalName = file.getOriginalFilename();
        String extension = extensionOf(originalName);
        String storedName = uniqueName(extension);
        Path uploadRoot = Path.of(fileProperty.getUploadDir()).toAbsolutePath().normalize();
        Path targetPath = uploadRoot.resolve(storedName).normalize();

        if (!targetPath.startsWith(uploadRoot)) {
            throw new IOException("Invalid storage path");
        }

        Files.createDirectories(uploadRoot);
        MessageDigest digest = sha256Digest();
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            Files.copy(input, targetPath);
        }

        String storagePath = targetPath.toString();
        return new StoredFile(
                storagePath,
                toAccessUrl(storagePath),
                originalName,
                storedName,
                hex(digest.digest()),
                file.getContentType(),
                file.getSize()
        );
    }

    @Override
    public String toAccessUrl(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }
        String fileName = Path.of(storagePath).getFileName().toString();
        String accessPath = fileProperty.getAccessPath();
        if (!accessPath.endsWith("/")) {
            accessPath = accessPath + "/";
        }
        return accessPath + fileName;
    }

    @Override
    public String resolveStoragePath(String accessUrl) {
        if (accessUrl == null || accessUrl.isBlank()) {
            return null;
        }
        String accessPath = fileProperty.getAccessPath();
        String normalizedAccessPath = accessPath.endsWith("/") ? accessPath : accessPath + "/";
        if (!accessUrl.startsWith(normalizedAccessPath)) {
            return null;
        }
        String fileName = accessUrl.substring(normalizedAccessPath.length());
        if (fileName.isBlank()) {
            return null;
        }
        Path uploadRoot = Path.of(fileProperty.getUploadDir()).toAbsolutePath().normalize();
        Path targetPath = uploadRoot.resolve(fileName).normalize();
        if (!targetPath.startsWith(uploadRoot)) {
            return null;
        }
        return targetPath.toString();
    }

    @Override
    public boolean delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return false;
        }
        try {
            Path uploadRoot = Path.of(fileProperty.getUploadDir()).toAbsolutePath().normalize();
            Path targetPath = Path.of(storagePath).toAbsolutePath().normalize();
            if (!targetPath.startsWith(uploadRoot)) {
                return false;
            }
            return Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            return false;
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > fileProperty.getMaxFileSize()) {
            throw new IllegalArgumentException("File is too large");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are supported");
        }
        String extension = extensionOf(file.getOriginalFilename());
        for (String allowed : fileProperty.getAllowedImageTypes()) {
            if (allowed.equalsIgnoreCase(extension)) {
                return;
            }
        }
        throw new IllegalArgumentException("Unsupported image type");
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String uniqueName(String extension) {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return timestamp + "_" + uuid + "." + extension;
    }

    private MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available", e);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
