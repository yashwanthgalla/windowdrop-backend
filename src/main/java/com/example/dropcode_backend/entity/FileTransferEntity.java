package com.example.dropcode_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "file_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileTransferEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "transfer_code_hash", unique = true, length = 128)
    private String transferCodeHash;

    @Column(name = "code_salt", length = 64)
    private String codeSalt;

    @Column(name = "code_preview", length = 8)
    private String codePreview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private UserEntity senderUser;

    @Column(name = "sender_display_name", length = 255)
    private String senderDisplayName;

    @Column(name = "original_filename", length = 512)
    private String originalFilename;

    @Column(name = "file_size_bytes", nullable = false)
    @Builder.Default
    private Long fileSizeBytes = 0L;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "has_folder", nullable = false)
    @Builder.Default
    private Boolean hasFolder = false;

    @Column(name = "transfer_status", nullable = false, length = 32)
    @Builder.Default
    private String transferStatus = "CREATED";

    @Column(name = "storage_path", length = 512)
    private String storagePath;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private Integer failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TransferItemEntity> items = new ArrayList<>();

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TransferRecipientEntity> recipients = new ArrayList<>();

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (fileSizeBytes == null) fileSizeBytes = 0L;
        if (hasFolder == null) hasFolder = false;
        if (failedAttempts == null) failedAttempts = 0;
        if (transferStatus == null) transferStatus = "CREATED";
    }
}
