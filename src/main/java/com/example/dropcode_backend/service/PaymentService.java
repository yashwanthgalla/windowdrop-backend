package com.example.dropcode_backend.service;

import com.example.dropcode_backend.dto.BillingDtos;
import com.example.dropcode_backend.entity.PaymentEntity;
import com.example.dropcode_backend.entity.SubscriptionEntity;
import com.example.dropcode_backend.entity.SubscriptionPlanEntity;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.exception.ApiException;
import com.example.dropcode_backend.repository.PaymentRepository;
import com.example.dropcode_backend.repository.SubscriptionPlanRepository;
import com.example.dropcode_backend.util.CryptoUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionService subscriptionService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Value("${dropcode.razorpay.key-id:rzp_test_dropcode123}")
    private String razorpayKeyId;

    @Value("${dropcode.razorpay.key-secret:test_secret_dropcode_456}")
    private String razorpayKeySecret;

    @Value("${dropcode.razorpay.webhook-secret:test_webhook_dropcode_789}")
    private String razorpayWebhookSecret;

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
        String internalOrderId = "ord_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String rzpOrderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        try {
            if (!razorpayKeyId.startsWith("rzp_test_dropcode") && !razorpayKeySecret.startsWith("test_secret")) {
                RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
                JSONObject orderRequest = new JSONObject();
                orderRequest.put("amount", amountPaise);
                orderRequest.put("currency", "INR");
                orderRequest.put("receipt", internalOrderId);
                orderRequest.put("notes", new JSONObject().put("userId", user.getId()).put("planCode", plan.getPlanCode()));

                com.razorpay.Order order = razorpay.orders.create(orderRequest);
                rzpOrderId = order.get("id");
                log.info("Created real Razorpay order: {}", rzpOrderId);
            } else {
                log.info("Created local test Razorpay order: {}", rzpOrderId);
            }
        } catch (Exception e) {
            log.warn("Razorpay API call failed or in dev mode: {}. Falling back to test order.", e.getMessage());
        }

        PaymentEntity payment = PaymentEntity.builder()
                .user(user)
                .planCode(plan.getPlanCode())
                .provider("RAZORPAY")
                .providerOrderId(rzpOrderId)
                .amountPaise(amountPaise)
                .currency("INR")
                .paymentStatus("PENDING")
                .providerSignatureVerified(false)
                .createdAt(Instant.now())
                .build();

        paymentRepository.save(payment);

        return BillingDtos.OrderResponse.builder()
                .orderId(rzpOrderId)
                .paymentSessionId(rzpOrderId)
                .amountPaise(amountPaise)
                .planCode(plan.getPlanCode())
                .cfOrderId(rzpOrderId)
                .environment("sandbox")
                .razorpayOrderId(rzpOrderId)
                .razorpayKeyId(razorpayKeyId)
                .currency("INR")
                .build();
    }

    @Transactional
    public BillingDtos.VerifyOrderResponse verifyPayment(BillingDtos.VerifyOrderRequest request) {
        String orderId = request.getRazorpayOrderId() != null && !request.getRazorpayOrderId().isEmpty()
                ? request.getRazorpayOrderId()
                : request.getOrderId();

        PaymentEntity payment = paymentRepository.findByProviderOrderId(orderId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Order record not found", HttpStatus.NOT_FOUND));

        if ("SUCCESS".equalsIgnoreCase(payment.getPaymentStatus())) {
            BillingDtos.EntitlementDto ent = subscriptionService.getCurrentEntitlement();
            return BillingDtos.VerifyOrderResponse.builder().success(true).entitlement(ent).build();
        }

        boolean verified = false;

        // If mock dev mode or test order verification
        if ((request.getRazorpaySignature() == null || request.getRazorpaySignature().isEmpty()) 
                && (razorpayKeySecret.startsWith("test_secret") || orderId.startsWith("order_"))) {
            log.info("Simulated test checkout verified for order: {}", orderId);
            verified = true;
        } else if (request.getRazorpayPaymentId() != null && request.getRazorpaySignature() != null) {
            String payload = orderId + "|" + request.getRazorpayPaymentId();
            String expectedSignature = CryptoUtils.hmacSha256(payload, razorpayKeySecret);
            if (expectedSignature.equalsIgnoreCase(request.getRazorpaySignature())) {
                verified = true;
                log.info("Verified Razorpay payment signature for order: {}", orderId);
            } else {
                log.warn("Invalid Razorpay payment signature for order: {}", orderId);
            }
        }

        if (!verified) {
            payment.setPaymentStatus("FAILED");
            paymentRepository.save(payment);
            throw new ApiException("PAYMENT_FAILED", "Payment signature verification failed", HttpStatus.BAD_REQUEST);
        }

        payment.setPaymentStatus("SUCCESS");
        payment.setProviderPaymentId(request.getRazorpayPaymentId() != null ? request.getRazorpayPaymentId() : "pay_" + UUID.randomUUID().toString().substring(0, 12));
        payment.setProviderSignatureVerified(true);
        payment.setPaidAt(Instant.now());

        SubscriptionEntity sub = subscriptionService.activateSubscription(payment.getUser(), payment.getPlanCode());
        payment.setSubscription(sub);
        paymentRepository.save(payment);

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

    @Transactional
    public void handleRazorpayWebhook(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isEmpty()) {
            throw new ApiException("UNAUTHORIZED", "Missing webhook signature", HttpStatus.UNAUTHORIZED);
        }

        String expectedSignature = CryptoUtils.hmacSha256(payload, razorpayWebhookSecret);
        if (!expectedSignature.equalsIgnoreCase(signatureHeader)) {
            log.warn("Webhook signature mismatch.");
            throw new ApiException("UNAUTHORIZED", "Invalid webhook signature", HttpStatus.UNAUTHORIZED);
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText();
            log.info("Processing Razorpay webhook event: {}", event);

            if ("order.paid".equalsIgnoreCase(event) || "payment.captured".equalsIgnoreCase(event)) {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                String orderId = paymentEntity.path("order_id").asText();
                String paymentId = paymentEntity.path("id").asText();

                Optional<PaymentEntity> paymentOpt = paymentRepository.findByProviderOrderId(orderId);
                if (paymentOpt.isPresent()) {
                    PaymentEntity payment = paymentOpt.get();
                    if (!"SUCCESS".equalsIgnoreCase(payment.getPaymentStatus())) {
                        payment.setPaymentStatus("SUCCESS");
                        payment.setProviderPaymentId(paymentId);
                        payment.setProviderSignatureVerified(true);
                        payment.setPaidAt(Instant.now());

                        SubscriptionEntity sub = subscriptionService.activateSubscription(payment.getUser(), payment.getPlanCode());
                        payment.setSubscription(sub);
                        paymentRepository.save(payment);
                        log.info("Activated subscription for user {} via webhook", payment.getUser().getId());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing Razorpay webhook payload: {}", e.getMessage(), e);
            throw new ApiException("WEBHOOK_ERROR", "Failed to process webhook", HttpStatus.BAD_REQUEST);
        }
    }
}
