package com.example.dropcode_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dropcode")
@Data
public class DropcodeProperties {

    private Cors cors = new Cors();
    private Storage storage = new Storage();
    private Security security = new Security();
    private Firebase firebase = new Firebase();
    private Limits limits = new Limits();

    @Data
    public static class Cors {
        private String allowedOrigins = "http://localhost:5173,http://localhost:3000";
    }

    @Data
    public static class Storage {
        private String localDir = "./staging-data";
        private boolean s3Enabled = false;
        private String s3Bucket = "dropcode-staging-buffer";
        private String s3Region = "ap-south-1";
    }

    @Data
    public static class Security {
        private String stagingMasterKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        private String codePepper = "dropcode-secure-code-pepper-key-minimum-32-chars-long";
        private String ipEncryptionKey = "12345678901234567890123456789012";
    }

    @Data
    public static class Firebase {
        private String serviceAccountPath = "";
        private String projectId = "dropcode-7568e";
    }

    @Data
    public static class Limits {
        private int codeTtlSeconds = 300;
        private int downloadWindowSeconds = 900;
        private int maxCodeAttempts = 5;
        private int lockoutDurationSeconds = 900;
        private long freeLimitBytes = 500000000L;
        private long standardLimitBytes = 5000000000L;
        private long maxTransferBytes = 10000000000L;
        private int auditRetentionDays = 90;
    }
}
