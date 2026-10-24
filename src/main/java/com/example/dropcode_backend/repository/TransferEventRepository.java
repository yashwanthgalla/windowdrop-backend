package com.example.dropcode_backend.repository;

import com.example.dropcode_backend.entity.TransferEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferEventRepository extends JpaRepository<TransferEventEntity, Long> {
    List<TransferEventEntity> findByTransferIdOrderByEventTimestampAsc(String transferId);
}
