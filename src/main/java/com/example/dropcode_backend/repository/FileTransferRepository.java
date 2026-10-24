package com.example.dropcode_backend.repository;

import com.example.dropcode_backend.entity.FileTransferEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileTransferRepository extends JpaRepository<FileTransferEntity, String> {

    Optional<FileTransferEntity> findByTransferCodeHash(String transferCodeHash);

    @Query("SELECT t FROM FileTransferEntity t WHERE t.senderUser.id = :userId ORDER BY t.createdAt DESC")
    List<FileTransferEntity> findSentHistory(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT t FROM FileTransferEntity t WHERE t.senderUser.id = :userId AND t.createdAt < :beforeCursor ORDER BY t.createdAt DESC")
    List<FileTransferEntity> findSentHistoryBeforeCursor(@Param("userId") Long userId, @Param("beforeCursor") Instant beforeCursor, Pageable pageable);

    @Query("SELECT t FROM FileTransferEntity t WHERE t.expiresAt < :now AND t.transferStatus IN ('CREATED', 'WAITING_FOR_RECEIVER', 'READY', 'TRANSFERRING', 'CLAIMED')")
    List<FileTransferEntity> findExpiredTransfers(@Param("now") Instant now);

    @Query("SELECT COUNT(t) FROM FileTransferEntity t WHERE t.senderUser.id = :userId")
    long countBySenderUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(t.fileSizeBytes), 0) FROM FileTransferEntity t WHERE t.senderUser.id = :userId")
    long sumSentBytesByUserId(@Param("userId") Long userId);
}
