package com.hw.manage.Service.storage;

public record StoredFile(
        String storagePath,
        String accessUrl,
        String originalName,
        String storedName,
        String contentHash,
        String mimeType,
        long sizeBytes
) {
}
