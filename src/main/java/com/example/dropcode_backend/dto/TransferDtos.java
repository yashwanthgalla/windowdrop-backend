package com.example.dropcode_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class TransferDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateTransferRequest {
        private List<QuoteDtos.QuoteItemDto> items;
        private boolean hasFolder;
        private String senderName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemMapping {
        private int index;
        private String itemId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateTransferResponse {
        private String transferId;
        private List<ItemMapping> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CompleteTransferResponse {
        private String code;
        private String expiresAt;
        private String state; // 'ready'
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransferStatusResponse {
        private String state; // 'uploading' | 'ready' | 'claimed' | 'delivered' | 'expired' | 'cancelled'
        private String expiresAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VerifyCodeRequest {
        private String code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReceiverItemDto {
        private String id;
        private String name;
        private long size;
        private String path;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SenderInfoDto {
        private String displayName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VerifyCodeResponse {
        private String transferId;
        private SenderInfoDto sender;
        private List<ReceiverItemDto> items;
        private long totalSize;
        private String downloadToken;
        private String downloadExpiresAt;
        private String originalFilename;
        private boolean hasFolder;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReceiverItemStatusDto {
        private String id;
        private boolean downloaded;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReceiverStatusResponse {
        private String state;
        private List<ReceiverItemStatusDto> items;
        private String downloadExpiresAt;
    }
}
