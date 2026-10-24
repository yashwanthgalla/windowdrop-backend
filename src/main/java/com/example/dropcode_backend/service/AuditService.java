package com.example.dropcode_backend.service;

import com.example.dropcode_backend.entity.AccessAuditEntity;
import com.example.dropcode_backend.entity.FileTransferEntity;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.repository.AccessAuditRepository;
import com.example.dropcode_backend.util.CryptoUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AccessAuditRepository accessAuditRepository;

    @Value("${dropcode.security.ip-encryption-key:12345678901234567890123456789012}")
    private String ipEncryptionKey;

    @Value("${dropcode.limits.audit-retention-days:90}")
    private int auditRetentionDays;

    @Transactional
    public void recordAccess(UserEntity user, FileTransferEntity transfer, String eventType, HttpServletRequest request) {
        try {
            String clientIp = resolveClientIp(request);
            String userAgent = request != null ? request.getHeader("User-Agent") : null;
            if (userAgent != null && userAgent.length() > 500) {
                userAgent = userAgent.substring(0, 500);
            }

            byte[] keyBytes = ipEncryptionKey.length() == 32 
                    ? ipEncryptionKey.getBytes(StandardCharsets.UTF_8)
                    : CryptoUtils.deriveKey(ipEncryptionKey, "dropcode_ip_audit_key");

            byte[] encryptedIpBytes = CryptoUtils.encryptAesGcm(clientIp.getBytes(StandardCharsets.UTF_8), keyBytes);
            String encryptedIpHex = HexFormat.of().formatHex(encryptedIpBytes);

            AccessAuditEntity audit = AccessAuditEntity.builder()
                    .user(user)
                    .transfer(transfer)
                    .eventType(eventType)
                    .encryptedIpAddress(encryptedIpHex)
                    .ipKeyVersion(1)
                    .userAgent(userAgent)
                    .createdAt(Instant.now())
                    .retentionExpiresAt(Instant.now().plus(auditRetentionDays, ChronoUnit.DAYS))
                    .build();

            accessAuditRepository.save(audit);
        } catch (Exception e) {
            log.error("Failed to record security audit log: {}", e.getMessage());
        }
    }

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // First IP in the comma-separated list
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @Transactional
    public void purgeExpiredAudits() {
        int deleted = accessAuditRepository.deleteExpiredAudits(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired access audit records.", deleted);
        }
    }
}
