package com.askvocate.backend.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class MongoConfig {

    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    @Primary
    public MongoClient mongoClient() {
        String maskedUri = maskPassword(mongoUri);
        logger.info("========================================");
        logger.info("MONGODB CONNECTION");
        logger.info("URI: {}", maskedUri);
        logger.info("========================================");

        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(mongoUri))
                    .applyToSslSettings(builder -> {
                        try {
                            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                            sslContext.init(null, null, null);
                            builder.enabled(true).context(sslContext);
                        } catch (NoSuchAlgorithmException | KeyManagementException e) {
                            logger.error("Failed to configure TLS 1.2: {}", e.getMessage());
                            builder.enabled(true);
                        }
                    })
                    .build();

            MongoClient client = MongoClients.create(settings);
            logger.info("MongoDB client created successfully (TLS 1.2 forced)!");

            client.listDatabaseNames().first();
            logger.info("MongoDB connection test successful!");

            return client;
        } catch (Exception e) {
            logger.error("Failed to create MongoDB client: {}", e.getMessage(), e);
            throw e;
        }
    }

    private String maskPassword(String uri) {
        if (uri == null) return null;
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
