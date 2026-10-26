package com.askvocate.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    public S3StorageService(@Autowired(required = false) S3Client s3Client,
                            @Autowired(required = false) S3Presigner s3Presigner,
                            @Value("${aws.s3.bucket-name:}") String bucketName) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName != null ? bucketName.trim() : "";
    }

    public boolean isConfigured() {
        return s3Client != null && !bucketName.isBlank();
    }

    public String uploadDocument(MultipartFile file, String userId, String documentType, String side) throws IOException {
        if (!isConfigured()) {
            throw new IllegalStateException("AWS S3 is not configured.");
        }
        String key = String.format("documents/%s/%s/%s_%s", userId, documentType, side, UUID.randomUUID());
        return uploadBytes(file.getBytes(), key, file.getContentType());
    }

    public String uploadBytes(byte[] bytes, String key, String contentType) {
        if (!isConfigured()) {
            throw new IllegalStateException("AWS S3 is not configured.");
        }
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType != null ? contentType : "image/jpeg")
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            log.info("Uploaded document to S3: bucket={}, key={}", bucketName, key);
            return key;
        } catch (Exception e) {
            log.error("Failed to upload document to S3 key={}: {}", key, e.getMessage());
            throw new RuntimeException("S3 upload failed: " + e.getMessage(), e);
        }
    }

    public String getPresignedUrl(String key, int durationMinutes) {
        if (s3Presigner == null || bucketName.isBlank() || key == null || key.isBlank()) {
            return null;
        }
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(durationMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.warn("Could not generate S3 pre-signed URL for key={}: {}", key, e.getMessage());
            return null;
        }
    }

    public byte[] downloadBytes(String key) throws IOException {
        if (!isConfigured()) {
            throw new IllegalStateException("AWS S3 is not configured.");
        }
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            return s3Client.getObject(request).readAllBytes();
        } catch (Exception e) {
            throw new IOException("Failed to download from S3 key=" + key + ": " + e.getMessage(), e);
        }
    }

    public void deleteObject(String key) {
        if (!isConfigured() || key == null || key.isBlank()) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        } catch (Exception e) {
            log.warn("Failed to delete S3 object key={}: {}", key, e.getMessage());
        }
    }
}
