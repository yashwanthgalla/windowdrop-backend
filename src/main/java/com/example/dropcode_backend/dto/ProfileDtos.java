package com.example.dropcode_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ProfileDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserStatsDto {
        private long sentCount;
        private long receivedCount;
        private long totalSentBytes;
        private long totalReceivedBytes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProfileResponse {
        private String uid;
        private String email;
        private String displayName;
        private String username;
        private boolean emailVerified;
        private String createdAt;
        private BillingDtos.EntitlementDto entitlement;
        private UserStatsDto stats;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateProfileRequest {
        private String displayName;
        private String username;
    }
}
