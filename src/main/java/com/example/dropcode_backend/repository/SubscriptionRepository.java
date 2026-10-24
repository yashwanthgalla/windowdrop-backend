package com.example.dropcode_backend.repository;

import com.example.dropcode_backend.entity.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.user.id = :userId AND s.subscriptionStatus = 'ACTIVE' AND s.expiresAt > :now ORDER BY s.plan.maxFileSizeBytes DESC")
    List<SubscriptionEntity> findActiveSubscriptionsByUser(@Param("userId") Long userId, @Param("now") Instant now);

    default Optional<SubscriptionEntity> findHighestActiveSubscription(Long userId) {
        List<SubscriptionEntity> active = findActiveSubscriptionsByUser(userId, Instant.now());
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(0));
    }
}
