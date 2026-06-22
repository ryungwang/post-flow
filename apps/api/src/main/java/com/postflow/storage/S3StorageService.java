package com.postflow.storage;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

/**
 * AWS S3 storage for the {@code prod} profile.
 *
 * <p>Credentials come from {@link DefaultCredentialsProvider} (instance role / standard
 * {@code AWS_*} env). Bucket/region/prefix/public-base from {@link StorageProperties}.
 */
@Service
@Profile("prod")
public class S3StorageService implements StorageService {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String prefix;
    private final String publicBaseUrl;

    public S3StorageService(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        this.bucket = s3.bucket();
        this.prefix = s3.prefix() != null ? s3.prefix() : "";
        this.publicBaseUrl = s3.publicBaseUrl();
        Region region = Region.of(s3.region());
        this.client = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.presigner = S3Presigner.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public String upload(String key, InputStream content, long contentLength, String contentType) {
        String objectKey = withPrefix(key);
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromInputStream(content, contentLength));
        return key;
    }

    @Override
    public InputStream download(String key) {
        return client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(withPrefix(key))
                .build());
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + withPrefix(key);
    }

    @Override
    public String presignedUrl(String key, Duration ttl) {
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(withPrefix(key))
                        .build())
                .build();
        return presigner.presignGetObject(presign).url().toString();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(b -> b.bucket(bucket).key(withPrefix(key)));
    }

    private String withPrefix(String key) {
        return prefix.isEmpty() ? key : prefix + "/" + key;
    }

    @PreDestroy
    void close() {
        client.close();
        presigner.close();
    }
}
