package com.example.dropcode_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "access_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private FileTransferEntity transfer;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "encrypted_ip_address", nullable = false, length = 255)
    private String encryptedIpAddress;

    @Column(name = "ip_key_version", nullable = false)
    @Builder.Default
    private Integer ipKeyVersion = 1;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "retention_expires_at", nullable = false)
    private Instant retentionExpiresAt;

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (ipKeyVersion == null) ipKeyVersion = 1;
    }
}
