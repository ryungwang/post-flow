package com.postflow.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * Storage abstraction. Threads requires publicly reachable media URLs (Meta downloads
 * the file server-side), so publish flows use {@link #publicUrl}. Implementations are
 * selected by Spring profile: local filesystem (dev) vs AWS S3 (prod).
 *
 * <p>Do not depend on a specific SDK (S3/MinIO) outside the prod implementation
 * (global infra rule: file storage abstraction).
 */
public interface StorageService {

    /** Upload bytes under {@code key}; returns the stored key. */
    String upload(String key, InputStream content, long contentLength, String contentType);

    /** Open a stored object for reading. */
    InputStream download(String key);

    /** Publicly reachable URL for {@code key} (used as image_url/video_url on publish). */
    String publicUrl(String key);

    /** Time-limited signed URL for private access. */
    String presignedUrl(String key, Duration ttl);

    /** Delete the stored object. */
    void delete(String key);
}
