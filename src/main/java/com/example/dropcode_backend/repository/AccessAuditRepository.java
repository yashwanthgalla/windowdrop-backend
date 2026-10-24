package com.example.dropcode_backend.repository;

import com.example.dropcode_backend.entity.AccessAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AccessAuditRepository extends JpaRepository<AccessAuditEntity, Long> {

    List<AccessAuditEntity> findByTransferIdOrderByCreatedAtDesc(String transferId);

    @Modifying
    @Query("DELETE FROM AccessAuditEntity a WHERE a.retentionExpiresAt < :now")
    int deleteExpiredAudits(@Param("now") Instant now);
}
