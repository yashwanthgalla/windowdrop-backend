package com.example.dropcode_backend.repository;

import com.example.dropcode_backend.entity.TransferRecipientEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransferRecipientRepository extends JpaRepository<TransferRecipientEntity, Long> {

    List<TransferRecipientEntity> findByTransferId(String transferId);

    Optional<TransferRecipientEntity> findByTransferIdAndDownloadTokenHash(String transferId, String downloadTokenHash);

    @Query("SELECT r FROM TransferRecipientEntity r WHERE r.receiverUser.id = :userId ORDER BY r.createdAt DESC")
    List<TransferRecipientEntity> findReceivedHistory(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT r FROM TransferRecipientEntity r WHERE r.receiverUser.id = :userId AND r.createdAt < :beforeCursor ORDER BY r.createdAt DESC")
    List<TransferRecipientEntity> findReceivedHistoryBeforeCursor(@Param("userId") Long userId, @Param("beforeCursor") Instant beforeCursor, Pageable pageable);

    @Query("SELECT COUNT(r) FROM TransferRecipientEntity r WHERE r.receiverUser.id = :userId")
    long countByReceiverUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(r.transfer.fileSizeBytes), 0) FROM TransferRecipientEntity r WHERE r.receiverUser.id = :userId")
    long sumReceivedBytesByUserId(@Param("userId") Long userId);
}
