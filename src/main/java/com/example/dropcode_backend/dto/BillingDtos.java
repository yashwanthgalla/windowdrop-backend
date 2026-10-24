package com.example.dropcode_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class BillingDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanDto {
        private String code;
        private String name;
        private long pricePaise;
        private long maxBytes;
        private int validDays;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlansResponse {
        private List<PlanDto> plans;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EntitlementDto {
        private String id;
        private String planCode;
        private String planName;
        private String startsAt;
        private String expiresAt;
        private String status;
        private long maxBytes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateOrderRequest {
        private String planCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderResponse {
        private String orderId;
        private String paymentSessionId;
        private long amountPaise;
        private String planCode;
        private String cfOrderId;
        private String environment; // 'sandbox' | 'production'
        private String currency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VerifyOrderRequest {
        private String orderId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VerifyOrderResponse {
        private boolean success;
        private EntitlementDto entitlement;
    }
}
