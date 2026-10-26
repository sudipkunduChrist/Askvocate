package com.askvocate.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    private static final Logger logger = LoggerFactory.getLogger(S3Config.class);

    @Value("${aws.s3.access-key}")
    private String accessKey;

    @Value("${aws.s3.secret-key}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Bean
    public S3Client s3Client() {
        logger.info("========================================");
        logger.info("☁️  AWS S3 CONNECTION");
        logger.info("📍 Region: {}", region);
        logger.info("📍 Bucket: {}", bucketName);
        logger.info("📍 Access Key: {}", accessKey != null && accessKey.length() > 4
                ? "****" + accessKey.substring(accessKey.length() - 4) : "null/short");
        logger.info("========================================");

        if (accessKey == null || accessKey.isBlank() ||
                secretKey == null || secretKey.isBlank() ||
                region == null || region.isBlank() ||
                bucketName == null || bucketName.isBlank()) {
            logger.warn("⚠️  AWS S3 credentials are incomplete or missing!");
        }

        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            S3Client client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
            logger.info("✅ AWS S3 client created successfully for bucket: {}", bucketName);
            return client;
        } catch (Exception e) {
            logger.error("❌ Failed to create AWS S3 client: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
