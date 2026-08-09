package com.askvocate.backend.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MongoConfig {

    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    @Primary
    public MongoClient mongoClient() {
        // Log the connection URI (mask password)
        String maskedUri = maskPassword(mongoUri);
        logger.info("========================================");
        logger.info("🔵 MONGODB CONNECTION");
        logger.info("📍 URI: {}", maskedUri);
        logger.info("========================================");

        try {
            MongoClient client = MongoClients.create(mongoUri);
            logger.info("✅ MongoDB client created successfully!");

            // Test connection
            client.listDatabaseNames().first();
            logger.info("✅ MongoDB connection test successful!");

            return client;
        } catch (Exception e) {
            logger.error("❌ Failed to create MongoDB client: {}", e.getMessage(), e);
            throw e;
        }
    }

    private String maskPassword(String uri) {
        if (uri == null) return null;
        // Mask password in URI: mongodb+srv://user:password@host -> mongodb+srv://user:****@host
        try {
            int start = uri.indexOf("://") + 3;
            int atIndex = uri.indexOf("@");
            if (start > 0 && atIndex > start) {
                String beforeAt = uri.substring(start, atIndex);
                int colonIndex = beforeAt.indexOf(":");
                if (colonIndex > 0) {
                    String user = beforeAt.substring(0, colonIndex);
                    return uri.substring(0, start) + user + ":****" + uri.substring(atIndex);
                }
            }
        } catch (Exception ignored) {}
        return uri;
    }
}