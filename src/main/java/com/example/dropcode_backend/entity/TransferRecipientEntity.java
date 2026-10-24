package com.example.dropcode_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "transfer_recipients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRecipientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private FileTransferEntity transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_user_id")
    private UserEntity receiverUser;

    @Column(name = "receiver_email", length = 255)
    private String receiverEmail;

    @Column(name = "receiver_display_name", length = 255)
    private String receiverDisplayName;

    @Column(name = "recipient_status", nullable = false, length = 32)
    @Builder.Default
    private String recipientStatus = "CLAIMED";

    @Column(name = "download_token_hash", length = 128)
    private String downloadTokenHash;

    @Column(name = "download_token_expires_at")
    private Instant downloadTokenExpiresAt;

    @Column(name = "first_accessed_at")
    private Instant firstAccessedAt;

    @Column(name = "downloaded_at")
    private Instant downloadedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (recipientStatus == null) recipientStatus = "CLAIMED";
    }
}
