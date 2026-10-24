package com.example.dropcode_backend.repository;

import com.example.dropcode_backend.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByProviderOrderId(String providerOrderId);
    Optional<PaymentEntity> findByProviderPaymentId(String providerPaymentId);
    List<PaymentEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
