package com.example.dropcode_backend;

import com.example.dropcode_backend.dto.BillingDtos;
import com.example.dropcode_backend.entity.PaymentEntity;
import com.example.dropcode_backend.entity.SubscriptionEntity;
import com.example.dropcode_backend.entity.SubscriptionPlanEntity;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.exception.ApiException;
import com.example.dropcode_backend.repository.PaymentRepository;
import com.example.dropcode_backend.repository.SubscriptionPlanRepository;
import com.example.dropcode_backend.service.PaymentService;
import com.example.dropcode_backend.service.SubscriptionService;
import com.example.dropcode_backend.service.UserService;
import com.example.dropcode_backend.util.CryptoUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAndWebhookTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private UserService userService;

    private PaymentService paymentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String KEY_ID = "rzp_test_dropcode123";
    private final String KEY_SECRET = "test_secret_dropcode_456";
    private final String WEBHOOK_SECRET = "test_webhook_dropcode_789";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                planRepository,
                subscriptionService,
                userService,
                objectMapper
        );
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", KEY_ID);
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", KEY_SECRET);
        ReflectionTestUtils.setField(paymentService, "razorpayWebhookSecret", WEBHOOK_SECRET);
    }

    @Test
    @DisplayName("Create payment order looks up price from plan definition in MySQL")
    void testCreatePaymentOrder() {
        UserEntity user = UserEntity.builder().id(1L).email("user@dropcode.local").build();
        when(userService.getRequiredCurrentUser()).thenReturn(user);

        SubscriptionPlanEntity plan = SubscriptionPlanEntity.builder()
                .planCode("standard")
                .pricePaise(4900L)
                .currency("INR")
                .build();
        when(planRepository.findByPlanCode("standard")).thenReturn(Optional.of(plan));

        BillingDtos.CreateOrderRequest req = BillingDtos.CreateOrderRequest.builder()
                .planCode("standard")
                .build();

        BillingDtos.OrderResponse res = paymentService.createOrder(req);

        assertNotNull(res);
        assertEquals(4900L, res.getAmountPaise());
        assertEquals("standard", res.getPlanCode());
        assertNotNull(res.getOrderId());

        ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(captor.capture());
        PaymentEntity saved = captor.getValue();
        assertEquals("PENDING", saved.getPaymentStatus());
        assertEquals(4900L, saved.getAmountPaise());
        assertEquals("RAZORPAY", saved.getProvider());
    }

    @Test
    @DisplayName("Valid payment signature activates subscription")
    void testVerifyPaymentValidSignature() {
        UserEntity user = UserEntity.builder().id(2L).build();
        String orderId = "order_test_12345";
        String paymentId = "pay_test_67890";

        PaymentEntity pendingPayment = PaymentEntity.builder()
                .id(10L)
                .user(user)
                .planCode("large")
                .providerOrderId(orderId)
                .amountPaise(7900L)
                .paymentStatus("PENDING")
                .build();

        when(paymentRepository.findByProviderOrderId(orderId)).thenReturn(Optional.of(pendingPayment));

        String payload = orderId + "|" + paymentId;
        String validSignature = CryptoUtils.hmacSha256(payload, KEY_SECRET);

        SubscriptionPlanEntity proPlan = SubscriptionPlanEntity.builder()
                .planCode("large")
                .planName("Pro Plan")
                .maxFileSizeBytes(10_000_000_000L)
                .build();

        SubscriptionEntity activeSub = SubscriptionEntity.builder()
                .id(100L)
                .user(user)
                .plan(proPlan)
                .subscriptionStatus("ACTIVE")
                .startedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(31536000))
                .build();

        when(subscriptionService.activateSubscription(user, "large")).thenReturn(activeSub);

        BillingDtos.VerifyOrderRequest verifyReq = BillingDtos.VerifyOrderRequest.builder()
                .orderId(orderId)
                .razorpayOrderId(orderId)
                .razorpayPaymentId(paymentId)
                .razorpaySignature(validSignature)
                .build();

        BillingDtos.VerifyOrderResponse res = paymentService.verifyPayment(verifyReq);

        assertTrue(res.isSuccess());
        assertNotNull(res.getEntitlement());
        assertEquals("large", res.getEntitlement().getPlanCode());
        assertEquals("SUCCESS", pendingPayment.getPaymentStatus());
        assertTrue(pendingPayment.getProviderSignatureVerified());
    }

    @Test
    @DisplayName("Invalid payment signature fails and throws ApiException")
    void testVerifyPaymentInvalidSignature() {
        String orderId = "order_test_99999";
        PaymentEntity pendingPayment = PaymentEntity.builder()
                .id(20L)
                .providerOrderId(orderId)
                .paymentStatus("PENDING")
                .build();

        when(paymentRepository.findByProviderOrderId(orderId)).thenReturn(Optional.of(pendingPayment));

        BillingDtos.VerifyOrderRequest verifyReq = BillingDtos.VerifyOrderRequest.builder()
                .orderId(orderId)
                .razorpayOrderId(orderId)
                .razorpayPaymentId("pay_tampered_111")
                .razorpaySignature("invalid_signature_hex_data")
                .build();

        assertThrows(ApiException.class, () -> paymentService.verifyPayment(verifyReq));
        assertEquals("FAILED", pendingPayment.getPaymentStatus());
    }

    @Test
    @DisplayName("Webhook with invalid signature is rejected with 401")
    void testWebhookInvalidSignature() {
        String payload = "{\"event\":\"order.paid\"}";
        String invalidSig = "bad_signature";

        assertThrows(ApiException.class, () -> paymentService.handleRazorpayWebhook(payload, invalidSig));
    }
}
