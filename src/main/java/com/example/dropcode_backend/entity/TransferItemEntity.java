package com.example.dropcode_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "transfer_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferItemEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private FileTransferEntity transfer;

    @Column(name = "item_index", nullable = false)
    private Integer itemIndex;

    @Column(name = "filename", nullable = false, length = 512)
    private String filename;

    @Column(name = "relative_path", nullable = false, length = 1024)
    private String relativePath;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "is_downloaded", nullable = false)
    @Builder.Default
    private Boolean isDownloaded = false;

    @Column(name = "storage_key", length = 512)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (isDownloaded == null) isDownloaded = false;
    }
}
