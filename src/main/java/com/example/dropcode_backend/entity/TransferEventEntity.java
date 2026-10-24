package com.example.dropcode_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "transfer_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private FileTransferEntity transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserEntity actorUser;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_status", nullable = false, length = 32)
    private String eventStatus;

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    @Builder.Default
    private Instant eventTimestamp = Instant.now();

    @Column(name = "sanitized_metadata", columnDefinition = "TEXT")
    private String sanitizedMetadata;

    @PrePersist
    public void onPrePersist() {
        if (eventTimestamp == null) eventTimestamp = Instant.now();
    }
}
