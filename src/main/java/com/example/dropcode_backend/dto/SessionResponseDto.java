package com.example.dropcode_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionResponseDto {
    private UserIdentityDto identity;
    private UserActivityDto activity;
    private EntitlementInfoDto entitlement;

    @Data
    @Builder
    public static class UserIdentityDto {
        private String kind; // 'user' | 'guest'
        private String id;
        private String displayName;
        private boolean emailVerified;
    }

    @Data
    @Builder
    public static class UserActivityDto {
        private String state; // 'idle' | 'sending' | 'receiving'
        private String transferId;
    }

    @Data
    @Builder
    public static class EntitlementInfoDto {
        private String planCode;
        private long maxBytes;
        private String expiresAt;
    }
}
