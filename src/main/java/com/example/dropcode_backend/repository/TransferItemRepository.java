package com.example.dropcode_backend.repository;

import com.example.dropcode_backend.entity.TransferItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransferItemRepository extends JpaRepository<TransferItemEntity, String> {
    List<TransferItemEntity> findByTransferIdOrderByItemIndexAsc(String transferId);
    Optional<TransferItemEntity> findByTransferIdAndId(String transferId, String itemId);
}
