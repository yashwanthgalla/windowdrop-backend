package com.example.dropcode_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class QuoteDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuoteItemDto {
        private String name;
        private long size;
        private String path;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuoteRequest {
        private List<QuoteItemDto> items;
        private boolean hasFolder;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuoteResponse {
        private boolean allowed;
        private String tier; // 'free' | 'standard' | 'large'
        private long pricePaise;
        private List<String> requirements; // 'LOGIN_REQUIRED' | 'EMAIL_NOT_VERIFIED' | 'PAYMENT_REQUIRED' | 'PLAN_REQUIRED_FOR_EXECUTABLES'
        private long totalBytes;
    }
}
