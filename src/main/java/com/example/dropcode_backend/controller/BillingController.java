package com.example.dropcode_backend.controller;

import com.example.dropcode_backend.dto.BillingDtos;
import com.example.dropcode_backend.service.PaymentService;
import com.example.dropcode_backend.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Billing & Subscriptions", description = "Plan pricing, entitlement check, and Razorpay payment checkout")
public class BillingController {

    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;

    @GetMapping({"/api/v1/billing/plans", "/api/v1/subscriptions/plans"})
    @Operation(summary = "Get available subscription plans (Free ₹0, Premium ₹49, Pro ₹79)")
    public ResponseEntity<BillingDtos.PlansResponse> getPlans() {
        return ResponseEntity.ok(subscriptionService.getActivePlans());
    }

    @GetMapping({"/api/v1/billing/entitlement", "/api/v1/subscriptions/me"})
    @Operation(summary = "Get current user subscription entitlement")
    public ResponseEntity<BillingDtos.EntitlementDto> getEntitlement() {
        return ResponseEntity.ok(subscriptionService.getCurrentEntitlement());
    }

    @PostMapping({"/api/v1/billing/orders", "/api/v1/subscriptions/checkout"})
    @Operation(summary = "Create Razorpay payment order for subscription upgrade")
    public ResponseEntity<BillingDtos.OrderResponse> createOrder(@RequestBody BillingDtos.CreateOrderRequest request) {
        return ResponseEntity.ok(paymentService.createOrder(request));
    }

    @PostMapping("/api/v1/billing/verify")
    @Operation(summary = "Verify Razorpay payment signature and activate 1-year entitlement")
    public ResponseEntity<BillingDtos.VerifyOrderResponse> verifyOrder(@RequestBody BillingDtos.VerifyOrderRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(request));
    }
}
