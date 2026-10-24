package com.example.dropcode_backend.repository;

import com.example.dropcode_backend.entity.SubscriptionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, Long> {
    Optional<SubscriptionPlanEntity> findByPlanCode(String planCode);
    List<SubscriptionPlanEntity> findByIsActiveTrue();
}
