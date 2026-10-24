package com.example.dropcode_backend.controller;

import com.example.dropcode_backend.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Payment Webhooks", description = "Server-to-server webhook endpoint for verified payment gateway callbacks")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/razorpay")
    @Operation(summary = "Razorpay server webhook endpoint with HMAC signature validation")
    public ResponseEntity<Map<String, String>> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {

        paymentService.handleRazorpayWebhook(payload, signature);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
