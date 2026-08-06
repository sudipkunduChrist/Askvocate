package com.askvocate.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {
    @Value("${firebase.credentials.path}")
    private String credentialsPath;

    @Value("${firebase.storage.bucket}")
    private String storageBucket;

    @Bean
    public Firestore firestore() throws IOException {
        FileInputStream serviceAccount = new FileInputStream(credentialsPath);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setStorageBucket(storageBucket)
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
        return FirestoreClient.getFirestore();
    }
}
