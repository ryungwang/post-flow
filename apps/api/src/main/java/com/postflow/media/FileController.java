package com.postflow.media;

import com.postflow.storage.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;

/**
 * Serves locally-stored files publicly (dev). In prod the StorageService returns S3 public
 * URLs directly, so this path is unused there.
 */
@RestController
public class FileController {

    private final StorageService storage;

    public FileController(StorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/files/{*key}")
    public ResponseEntity<InputStreamResource> serve(@PathVariable String key) {
        String cleaned = key.startsWith("/") ? key.substring(1) : key;
        try {
            InputStream in = storage.download(cleaned);
            return ResponseEntity.ok()
                    .contentType(contentType(cleaned))
                    .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(7)))
                    .body(new InputStreamResource(in));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "not found");
        }
    }

    private MediaType contentType(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
