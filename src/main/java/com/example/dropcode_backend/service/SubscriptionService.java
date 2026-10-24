package com.example.dropcode_backend.service;

import com.example.dropcode_backend.dto.BillingDtos;
import com.example.dropcode_backend.entity.SubscriptionEntity;
import com.example.dropcode_backend.entity.SubscriptionPlanEntity;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.repository.SubscriptionPlanRepository;
import com.example.dropcode_backend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public BillingDtos.PlansResponse getActivePlans() {
        List<SubscriptionPlanEntity> plans = planRepository.findByIsActiveTrue();
        // Return standard three plans: free, standard/premium, large/pro
        List<BillingDtos.PlanDto> dtos = plans.stream()
                .filter(p -> "free".equalsIgnoreCase(p.getPlanCode()) || "standard".equalsIgnoreCase(p.getPlanCode()) || "large".equalsIgnoreCase(p.getPlanCode()) || "premium".equalsIgnoreCase(p.getPlanCode()) || "pro".equalsIgnoreCase(p.getPlanCode()))
                .map(p -> BillingDtos.PlanDto.builder()
                        .code(p.getPlanCode())
                        .name(p.getPlanName())
                        .pricePaise(p.getPricePaise())
                        .maxBytes(p.getMaxFileSizeBytes())
                        .validDays(p.getBillingIntervalDays())
                        .build())
                .toList();

        return BillingDtos.PlansResponse.builder().plans(dtos).build();
    }

    @Transactional(readOnly = true)
    public BillingDtos.EntitlementDto getCurrentEntitlement() {
        Optional<UserEntity> userOpt = userService.getCurrentUser();
        if (userOpt.isEmpty()) {
            return null;
        }

        UserEntity user = userOpt.get();
        Optional<SubscriptionEntity> subOpt = subscriptionRepository.findHighestActiveSubscription(user.getId());

        if (subOpt.isEmpty()) {
            return null;
        }

        SubscriptionEntity sub = subOpt.get();
        return BillingDtos.EntitlementDto.builder()
                .id(String.valueOf(sub.getId()))
                .planCode(sub.getPlan().getPlanCode())
                .planName(sub.getPlan().getPlanName())
                .startsAt(sub.getStartedAt().toString())
                .expiresAt(sub.getExpiresAt().toString())
                .status(sub.getSubscriptionStatus())
                .maxBytes(sub.getPlan().getMaxFileSizeBytes())
                .build();
    }

    @Transactional
    public SubscriptionEntity activateSubscription(UserEntity user, String planCode) {
        String canonicalCode = planCode.toLowerCase();
        if ("premium".equals(canonicalCode)) canonicalCode = "standard";
        if ("pro".equals(canonicalCode)) canonicalCode = "large";

        SubscriptionPlanEntity plan = planRepository.findByPlanCode(canonicalCode)
                .or(() -> planRepository.findByPlanCode(planCode.toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan code: " + planCode));

        Instant now = Instant.now();
        Instant expires = now.plus(plan.getBillingIntervalDays(), ChronoUnit.DAYS);

        SubscriptionEntity sub = SubscriptionEntity.builder()
                .user(user)
                .plan(plan)
                .subscriptionStatus("ACTIVE")
                .startedAt(now)
                .expiresAt(expires)
                .build();

        return subscriptionRepository.save(sub);
    }
}
