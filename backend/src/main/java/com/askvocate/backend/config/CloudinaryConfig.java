package com.askvocate.backend.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryConfig {

    private static final Logger logger = LoggerFactory.getLogger(CloudinaryConfig.class);

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        logger.info("========================================");
        logger.info("☁️  CLOUDINARY CONNECTION");
        logger.info("📍 Cloud Name: {}", cloudName);
        logger.info("📍 API Key: {}", apiKey != null ? "****" + apiKey.substring(Math.max(0, apiKey.length() - 4)) : "null");
        logger.info("========================================");

        if (cloudName == null || cloudName.isEmpty() ||
                apiKey == null || apiKey.isEmpty() ||
                apiSecret == null || apiSecret.isEmpty()) {
            logger.warn("⚠️  Cloudinary credentials are incomplete or missing!");
            logger.warn("   cloudName: {}", cloudName != null ? "present" : "missing");
            logger.warn("   apiKey: {}", apiKey != null ? "present" : "missing");
            logger.warn("   apiSecret: {}", apiSecret != null ? "present" : "missing");
        }

        try {
            Map<String, String> config = ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true
            );

            Cloudinary cloudinary = new Cloudinary(config);
            logger.info("✅ Cloudinary client created successfully!");

            // Test connection by pinging
            try {
                cloudinary.api().ping(ObjectUtils.emptyMap());
                logger.info("✅ Cloudinary connection test successful!");
            } catch (Exception e) {
                logger.warn("⚠️  Cloudinary ping failed: {}", e.getMessage());
            }

            return cloudinary;
        } catch (Exception e) {
            logger.error("❌ Failed to create Cloudinary client: {}", e.getMessage(), e);
            throw e;
        }
    }
}