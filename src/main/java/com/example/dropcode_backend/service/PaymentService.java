package com.example.dropcode_backend.service;

import com.example.dropcode_backend.dto.BillingDtos;
import com.example.dropcode_backend.entity.PaymentEntity;
import com.example.dropcode_backend.entity.SubscriptionEntity;
import com.example.dropcode_backend.entity.SubscriptionPlanEntity;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.exception.ApiException;
import com.example.dropcode_backend.repository.PaymentRepository;
import com.example.dropcode_backend.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionService subscriptionService;
    private final UserService userService;

    @Transactional
    public BillingDtos.OrderResponse createOrder(BillingDtos.CreateOrderRequest request) {
        UserEntity user = userService.getRequiredCurrentUser();

        String planCode = request.getPlanCode() != null ? request.getPlanCode().toLowerCase() : "standard";
        String canonicalCode = planCode;
        if ("premium".equals(canonicalCode)) canonicalCode = "standard";
        if ("pro".equals(canonicalCode)) canonicalCode = "large";

        SubscriptionPlanEntity plan = planRepository.findByPlanCode(canonicalCode)
                .or(() -> planRepository.findByPlanCode(planCode))
                .orElseThrow(() -> new ApiException("INVALID_PLAN", "Selected plan not found", HttpStatus.BAD_REQUEST));

        long amountPaise = plan.getPricePaise();
        String orderId = "ord_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        PaymentEntity payment = PaymentEntity.builder()
                .user(user)
                .planCode(plan.getPlanCode())
                .provider("DIRECT")
                .providerOrderId(orderId)
                .amountPaise(amountPaise)
                .currency("INR")
                .paymentStatus("PENDING")
                .providerSignatureVerified(true)
                .createdAt(Instant.now())
                .build();

        paymentRepository.save(payment);
        log.info("Created direct payment order: {} for user: {}", orderId, user.getId());

        return BillingDtos.OrderResponse.builder()
                .orderId(orderId)
                .paymentSessionId(orderId)
                .amountPaise(amountPaise)
                .planCode(plan.getPlanCode())
                .cfOrderId(orderId)
                .environment("production")
                .currency("INR")
                .build();
    }

    @Transactional
    public BillingDtos.VerifyOrderResponse verifyPayment(BillingDtos.VerifyOrderRequest request) {
        String orderId = request.getOrderId();
        if (orderId == null || orderId.trim().isEmpty()) {
            throw new ApiException("INVALID_ORDER", "Order ID is required", HttpStatus.BAD_REQUEST);
        }

        PaymentEntity payment = paymentRepository.findByProviderOrderId(orderId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Order record not found", HttpStatus.NOT_FOUND));

        if ("SUCCESS".equalsIgnoreCase(payment.getPaymentStatus())) {
            BillingDtos.EntitlementDto ent = subscriptionService.getCurrentEntitlement();
            return BillingDtos.VerifyOrderResponse.builder().success(true).entitlement(ent).build();
        }

        payment.setPaymentStatus("SUCCESS");
        payment.setProviderPaymentId("pay_direct_" + UUID.randomUUID().toString().substring(0, 12));
        payment.setProviderSignatureVerified(true);
        payment.setPaidAt(Instant.now());

        SubscriptionEntity sub = subscriptionService.activateSubscription(payment.getUser(), payment.getPlanCode());
        payment.setSubscription(sub);
        paymentRepository.save(payment);

        log.info("Activated subscription {} for user {} after verifying order {}", sub.getId(), payment.getUser().getId(), orderId);

        BillingDtos.EntitlementDto entitlementDto = BillingDtos.EntitlementDto.builder()
                .id(String.valueOf(sub.getId()))
                .planCode(sub.getPlan().getPlanCode())
                .planName(sub.getPlan().getPlanName())
                .startsAt(sub.getStartedAt().toString())
                .expiresAt(sub.getExpiresAt().toString())
                .status("ACTIVE")
                .maxBytes(sub.getPlan().getMaxFileSizeBytes())
                .build();

        return BillingDtos.VerifyOrderResponse.builder()
                .success(true)
                .entitlement(entitlementDto)
                .build();
    }
}
