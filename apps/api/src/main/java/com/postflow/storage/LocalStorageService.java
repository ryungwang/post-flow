package com.postflow.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Local filesystem storage for the {@code local} profile.
 */
@Service
@Profile("local")
public class LocalStorageService implements StorageService {

    private final Path basePath;
    private final String publicBaseUrl;

    public LocalStorageService(StorageProperties properties) {
        String base = properties.local() != null && properties.local().basePath() != null
                ? properties.local().basePath() : "./var/storage";
        this.basePath = Path.of(base).toAbsolutePath().normalize();
        this.publicBaseUrl = properties.local() != null && properties.local().publicBaseUrl() != null
                ? properties.local().publicBaseUrl() : "http://localhost:8080/files";
    }

    @Override
    public String upload(String key, InputStream content, long contentLength, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new StorageException("Failed to store " + key, e);
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Failed to read " + key, e);
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    @Override
    public String presignedUrl(String key, Duration ttl) {
        // Local dev has no signing; serve the public URL.
        return publicUrl(key);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Failed to delete " + key, e);
        }
    }

    /** Resolve and guard against path traversal outside the storage root. */
    private Path resolve(String key) {
        Path target = basePath.resolve(key).normalize();
        if (!target.startsWith(basePath)) {
            throw new StorageException("Illegal storage key: " + key, null);
        }
        return target;
    }
}
