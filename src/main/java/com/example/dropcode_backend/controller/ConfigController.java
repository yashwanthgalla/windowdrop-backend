package com.example.dropcode_backend.controller;

import com.example.dropcode_backend.dto.AppConfigDto;
import com.example.dropcode_backend.dto.BillingDtos;
import com.example.dropcode_backend.dto.SessionResponseDto;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.service.SubscriptionService;
import com.example.dropcode_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Configuration & Session", description = "Application configuration and active session resolution")
public class ConfigController {

    private final UserService userService;
    private final SubscriptionService subscriptionService;

    @Value("${dropcode.limits.code-ttl-seconds:300}")
    private int codeTtlSeconds;

    @Value("${dropcode.limits.download-window-seconds:900}")
    private int downloadWindowSeconds;

    @Value("${dropcode.limits.max-code-attempts:5}")
    private int maxAttempts;

    @Value("${dropcode.limits.free-limit-bytes:500000000}")
    private long freeLimitBytes;

    @Value("${dropcode.limits.standard-limit-bytes:5000000000}")
    private long standardLimitBytes;

    @Value("${dropcode.limits.max-transfer-bytes:10000000000}")
    private long maxTransferBytes;

    @GetMapping("/config")
    @Operation(summary = "Get global application configuration")
    public ResponseEntity<AppConfigDto> getConfig() {
        AppConfigDto config = AppConfigDto.builder()
                .codeTtlSeconds(codeTtlSeconds)
                .downloadWindowSeconds(downloadWindowSeconds)
                .maxAttempts(maxAttempts)
                .freeLimitBytes(freeLimitBytes)
                .standardLimitBytes(standardLimitBytes)
                .maxTransferBytes(maxTransferBytes)
                .prices(Map.of("standardPaise", 4900L, "largePaise", 7900L))
                .planValidDays(365)
                .executableExtensions(List.of("exe", "msi", "bat", "cmd", "com", "scr", "ps1", "vbs", "sh"))
                .build();

        return ResponseEntity.ok(config);
    }

    @GetMapping("/session")
    @Operation(summary = "Get current authenticated user session and activity status")
    public ResponseEntity<SessionResponseDto> getSession() {
        Optional<UserEntity> userOpt = userService.getCurrentUser();
        BillingDtos.EntitlementDto ent = subscriptionService.getCurrentEntitlement();

        SessionResponseDto.UserIdentityDto identity;
        if (userOpt.isPresent()) {
            UserEntity u = userOpt.get();
            identity = SessionResponseDto.UserIdentityDto.builder()
                    .kind("user")
                    .id(u.getFirebaseUid())
                    .displayName(u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                    .emailVerified(true)
                    .build();
        } else {
            identity = SessionResponseDto.UserIdentityDto.builder()
                    .kind("guest")
                    .id("guest")
                    .displayName("Guest")
                    .emailVerified(false)
                    .build();
        }

        SessionResponseDto.EntitlementInfoDto entitlementInfo = null;
        if (ent != null) {
            entitlementInfo = SessionResponseDto.EntitlementInfoDto.builder()
                    .planCode(ent.getPlanCode())
                    .maxBytes(ent.getMaxBytes())
                    .expiresAt(ent.getExpiresAt())
                    .build();
        }

        SessionResponseDto response = SessionResponseDto.builder()
                .identity(identity)
                .activity(SessionResponseDto.UserActivityDto.builder().state("idle").build())
                .entitlement(entitlementInfo)
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/activity/heartbeat")
    @Operation(summary = "User activity heartbeat")
    public ResponseEntity<Map<String, Boolean>> heartbeat() {
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
