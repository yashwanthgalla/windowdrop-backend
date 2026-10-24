package com.example.dropcode_backend.repository;

import com.example.dropcode_backend.entity.UploadSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSessionEntity, Long> {
    Optional<UploadSessionEntity> findByStorageUploadId(String storageUploadId);
    Optional<UploadSessionEntity> findByTransferId(String transferId);
}
