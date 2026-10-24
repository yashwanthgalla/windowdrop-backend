package com.example.dropcode_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AppConfigDto {
    private int codeTtlSeconds;
    private int downloadWindowSeconds;
    private int maxAttempts;
    private long freeLimitBytes;
    private long standardLimitBytes;
    private long maxTransferBytes;
    private Map<String, Long> prices;
    private int planValidDays;
    private List<String> executableExtensions;
}
