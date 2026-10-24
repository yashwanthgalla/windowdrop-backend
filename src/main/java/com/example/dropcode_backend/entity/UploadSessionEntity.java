package com.example.dropcode_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "upload_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private FileTransferEntity transfer;

    @Column(name = "storage_upload_id", nullable = false, unique = true, length = 128)
    private String storageUploadId;

    @Column(name = "expected_size_bytes", nullable = false)
    private Long expectedSizeBytes;

    @Column(name = "upload_status", nullable = false, length = 32)
    @Builder.Default
    private String uploadStatus = "IN_PROGRESS";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (uploadStatus == null) uploadStatus = "IN_PROGRESS";
    }
}
