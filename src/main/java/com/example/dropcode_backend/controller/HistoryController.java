package com.example.dropcode_backend.controller;

import com.example.dropcode_backend.dto.HistoryDtos;
import com.example.dropcode_backend.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
@Tag(name = "Transfer History", description = "Authorized sent and received transfer history with IP privacy redaction")
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping
    @Operation(summary = "Get paginated user transfer history (role: sent | received)")
    public ResponseEntity<HistoryDtos.HistoryResponse> getHistory(
            @RequestParam(name = "role", defaultValue = "sent") String role,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(historyService.getHistory(role, cursor, limit));
    }

    @GetMapping("/sent")
    @Operation(summary = "Get sent transfer history")
    public ResponseEntity<HistoryDtos.HistoryResponse> getSentHistory(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(historyService.getHistory("sent", cursor, limit));
    }

    @GetMapping("/received")
    @Operation(summary = "Get received transfer history")
    public ResponseEntity<HistoryDtos.HistoryResponse> getReceivedHistory(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(historyService.getHistory("received", cursor, limit));
    }
}
