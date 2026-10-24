package com.example.dropcode_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class HistoryDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryItemDto {
        private String name;
        private String path;
        private long size;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryEntryDto {
        private String id;
        private String role; // 'sent' | 'received'
        private String counterpartName;
        private List<HistoryItemDto> items;
        private long totalBytes;
        private String state;
        private String createdAt;
        private String deliveredAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryResponse {
        private List<HistoryEntryDto> entries;
        private String nextCursor;
    }
}
