package com.postflow.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage config.
 *
 * <p>S3 env naming follows the global infra convention:
 * {@code AWS_S3_BUCKET}, {@code AWS_REGION}, {@code AWS_S3_PREFIX},
 * {@code AWS_S3_PUBLIC_BASE_URL} (public assets only — e.g. publish image URLs).
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        Local local,
        S3 s3
) {
    public record Local(
            /** filesystem root for the local profile */
            String basePath,
            /** base URL the app serves local files under */
            String publicBaseUrl
    ) {
    }

    public record S3(
            String bucket,
            String region,
            String prefix,
            String publicBaseUrl
    ) {
    }
}
