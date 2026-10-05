package com.hw.manage.Service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {
    StoredFile store(MultipartFile file) throws IOException;

    String toAccessUrl(String storagePath);

    String resolveStoragePath(String accessUrl);

    boolean delete(String storagePath);
}
