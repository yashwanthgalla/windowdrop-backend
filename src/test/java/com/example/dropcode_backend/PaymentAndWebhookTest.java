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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAndWebhookTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private UserService userService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                planRepository,
                subscriptionService,
                userService
        );
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
        assertEquals("DIRECT", saved.getProvider());
    }

    @Test
    @DisplayName("Verify payment order activates subscription entitlement")
    void testVerifyPaymentOrder() {
        UserEntity user = UserEntity.builder().id(2L).build();
        String orderId = "order_test_12345";

        PaymentEntity pendingPayment = PaymentEntity.builder()
                .id(10L)
                .user(user)
                .planCode("large")
                .providerOrderId(orderId)
                .amountPaise(7900L)
                .paymentStatus("PENDING")
                .build();

        when(paymentRepository.findByProviderOrderId(orderId)).thenReturn(Optional.of(pendingPayment));

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
                .build();

        BillingDtos.VerifyOrderResponse res = paymentService.verifyPayment(verifyReq);

        assertTrue(res.isSuccess());
        assertNotNull(res.getEntitlement());
        assertEquals("large", res.getEntitlement().getPlanCode());
        assertEquals("SUCCESS", pendingPayment.getPaymentStatus());
        assertTrue(pendingPayment.getProviderSignatureVerified());
    }

    @Test
    @DisplayName("Unknown order ID throws NOT_FOUND ApiException")
    void testVerifyPaymentOrderNotFound() {
        String orderId = "order_unknown_99999";
        when(paymentRepository.findByProviderOrderId(orderId)).thenReturn(Optional.empty());

        BillingDtos.VerifyOrderRequest verifyReq = BillingDtos.VerifyOrderRequest.builder()
                .orderId(orderId)
                .build();

        assertThrows(ApiException.class, () -> paymentService.verifyPayment(verifyReq));
    }

    @Test
    @DisplayName("Empty order ID throws INVALID_ORDER ApiException")
    void testVerifyPaymentMissingOrderId() {
        BillingDtos.VerifyOrderRequest verifyReq = BillingDtos.VerifyOrderRequest.builder()
                .orderId("")
                .build();

        assertThrows(ApiException.class, () -> paymentService.verifyPayment(verifyReq));
    }
}
