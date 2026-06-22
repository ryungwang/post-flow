package com.postflow.media;

import com.postflow.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Uploads media (images) for posts. Returns a publicly reachable URL (Threads needs one). */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Set<String> ALLOWED = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final StorageService storage;

    public MediaController(StorageService storage) {
        this.storage = storage;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> upload(@AuthenticationPrincipal Long userId,
                                      @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "max 8MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "image only");
        }
        String key = "media/" + userId + "/" + UUID.randomUUID() + extension(contentType);
        try {
            storage.upload(key, file.getInputStream(), file.getSize(), contentType);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "upload failed");
        }
        return Map.of("key", key, "url", storage.publicUrl(key));
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}
