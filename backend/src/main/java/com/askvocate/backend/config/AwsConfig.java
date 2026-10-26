package com.askvocate.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
public class AwsConfig {

    private static final Logger log = LoggerFactory.getLogger(AwsConfig.class);

    @Value("${aws.region:ap-south-1}")
    private String awsRegion;

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    private AwsCredentialsProvider credentialsProvider() {
        if (accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())
            );
        }
        try {
            return DefaultCredentialsProvider.create();
        } catch (Exception e) {
            log.warn("Default AWS credentials not found, falling back to AnonymousCredentialsProvider");
            return AnonymousCredentialsProvider.create();
        }
    }

    private Region region() {
        try {
            return Region.of(awsRegion != null && !awsRegion.isBlank() ? awsRegion.trim() : "ap-south-1");
        } catch (Exception e) {
            return Region.AP_SOUTH_1;
        }
    }

    @Bean
    public S3Client s3Client() {
        try {
            return S3Client.builder()
                    .region(region())
                    .credentialsProvider(credentialsProvider())
                    .build();
        } catch (Exception e) {
            log.warn("Could not initialize AWS S3Client: {}", e.getMessage());
            return null;
        }
    }

    @Bean
    public S3Presigner s3Presigner() {
        try {
            return S3Presigner.builder()
                    .region(region())
                    .credentialsProvider(credentialsProvider())
                    .build();
        } catch (Exception e) {
            log.warn("Could not initialize AWS S3Presigner: {}", e.getMessage());
            return null;
        }
    }

    @Bean
    public TextractClient textractClient() {
        try {
            return TextractClient.builder()
                    .region(region())
                    .credentialsProvider(credentialsProvider())
                    .build();
        } catch (Exception e) {
            log.warn("Could not initialize AWS TextractClient: {}", e.getMessage());
            return null;
        }
    }
}
