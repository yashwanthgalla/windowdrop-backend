package com.example.dropcode_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${dropcode.firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${dropcode.firebase.project-id:dropcode-7568e}")
    private String projectId;

    @PostConstruct
    public void initializeFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already initialized.");
            return;
        }

        try {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setProjectId(projectId);

            if (serviceAccountPath != null && !serviceAccountPath.trim().isEmpty()) {
                Path path = Paths.get(serviceAccountPath.trim());
                if (Files.exists(path)) {
                    try (InputStream serviceAccount = new FileInputStream(path.toFile())) {
                        builder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
                        log.info("Initialized Firebase with service account file: {}", serviceAccountPath);
                    }
                } else {
                    log.warn("Configured Firebase service account file not found: {}. Using default application credentials.", serviceAccountPath);
                    builder.setCredentials(GoogleCredentials.getApplicationDefault());
                }
            } else {
                // If no credentials file provided, try application default credentials or mock options for local dev
                try {
                    builder.setCredentials(GoogleCredentials.getApplicationDefault());
                    log.info("Initialized Firebase with Application Default Credentials for project: {}", projectId);
                } catch (Exception e) {
                    log.warn("Application default credentials not available. Initializing Firebase in development mode.");
                    builder.setCredentials(new MockDevGoogleCredentials());
                }
            }

            FirebaseApp.initializeApp(builder.build());
            log.info("FirebaseApp initialized successfully for project: {}", projectId);
        } catch (Exception ex) {
            log.error("Failed to initialize FirebaseApp. Continuing in dev fallback mode: {}", ex.getMessage());
            try {
                FirebaseOptions devOptions = FirebaseOptions.builder()
                        .setProjectId(projectId)
                        .setCredentials(new MockDevGoogleCredentials())
                        .build();
                FirebaseApp.initializeApp(devOptions);
            } catch (Exception fallbackEx) {
                log.warn("Firebase dev fallback already active or skipped: {}", fallbackEx.getMessage());
            }
        }
    }

    private static class MockDevGoogleCredentials extends GoogleCredentials {
        @Override
        public com.google.auth.oauth2.AccessToken refreshAccessToken() {
            return new com.google.auth.oauth2.AccessToken("mock_dev_token", java.util.Date.from(java.time.Instant.now().plusSeconds(3600)));
        }
    }
}
