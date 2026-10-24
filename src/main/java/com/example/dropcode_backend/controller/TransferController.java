package com.example.dropcode_backend.controller;

import com.example.dropcode_backend.dto.QuoteDtos;
import com.example.dropcode_backend.dto.TransferDtos;
import com.example.dropcode_backend.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers (Sender)", description = "Sender operations for initializing and uploading transfers")
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/quote")
    @Operation(summary = "Quote transfer size and evaluate plan requirements")
    public ResponseEntity<QuoteDtos.QuoteResponse> quote(@RequestBody QuoteDtos.QuoteRequest request) {
        return ResponseEntity.ok(transferService.quote(request));
    }

    @PostMapping
    @Operation(summary = "Create a new file transfer session")
    public ResponseEntity<TransferDtos.CreateTransferResponse> createTransfer(
            @RequestBody TransferDtos.CreateTransferRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(transferService.createTransfer(request, httpRequest));
    }

    @PutMapping(value = "/{transferId}/items/{itemId}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Upload raw binary stream for a specific transfer item")
    public ResponseEntity<Map<String, Boolean>> uploadItem(
            @PathVariable String transferId,
            @PathVariable String itemId,
            InputStream inputStream,
            HttpServletRequest httpRequest) throws IOException {
        transferService.uploadItem(transferId, itemId, inputStream, httpRequest);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{transferId}/complete")
    @Operation(summary = "Complete transfer and issue short-lived sharing code")
    public ResponseEntity<TransferDtos.CompleteTransferResponse> completeTransfer(
            @PathVariable String transferId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(transferService.completeTransfer(transferId, httpRequest));
    }

    @GetMapping("/{transferId}/status")
    @Operation(summary = "Get transfer progress status for sender")
    public ResponseEntity<TransferDtos.TransferStatusResponse> getStatus(@PathVariable String transferId) {
        return ResponseEntity.ok(transferService.getSenderStatus(transferId));
    }

    @DeleteMapping("/{transferId}")
    @Operation(summary = "Cancel transfer and immediately purge all temporary files")
    public ResponseEntity<Void> cancelTransfer(
            @PathVariable String transferId,
            HttpServletRequest httpRequest) {
        transferService.cancelTransfer(transferId, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
