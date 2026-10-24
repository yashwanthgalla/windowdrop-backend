package com.example.dropcode_backend.controller;

import com.example.dropcode_backend.dto.TransferDtos;
import com.example.dropcode_backend.entity.FileTransferEntity;
import com.example.dropcode_backend.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@Tag(name = "Receive Flow", description = "Receiver code verification and temporary file downloads")
public class ReceiveController {

    private final TransferService transferService;

    @PostMapping({"/api/v1/receive/verify", "/api/v1/transfers/resolve-code"})
    @Operation(summary = "Verify 8-character sharing code and obtain secure download token")
    public ResponseEntity<TransferDtos.VerifyCodeResponse> verifyCode(
            @RequestBody TransferDtos.VerifyCodeRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(transferService.verifyCode(request.getCode(), httpRequest));
    }

    @GetMapping("/api/v1/receive/{transferId}/status")
    @Operation(summary = "Get receiver download status and item progress")
    public ResponseEntity<TransferDtos.ReceiverStatusResponse> getReceiverStatus(@PathVariable String transferId) {
        return ResponseEntity.ok(transferService.getReceiverStatus(transferId));
    }

    @GetMapping("/api/v1/receive/{transferId}/items/{itemId}")
    @Operation(summary = "Stream decrypted item payload to receiver")
    public void downloadSingleItem(
            @PathVariable String transferId,
            @PathVariable String itemId,
            @RequestParam("t") String downloadToken,
            HttpServletRequest httpRequest,
            HttpServletResponse response) throws IOException {

        transferService.streamSingleItem(transferId, itemId, downloadToken, response, httpRequest);
    }

    @GetMapping("/api/v1/receive/{transferId}/zip")
    @Operation(summary = "Stream on-the-fly zip archive of all transfer items to receiver")
    public void downloadZip(
            @PathVariable String transferId,
            @RequestParam("t") String downloadToken,
            HttpServletRequest httpRequest,
            HttpServletResponse response) throws IOException {

        FileTransferEntity transfer = transferService.getTransferEntity(transferId);
        String zipName = (transfer.getOriginalFilename() != null && !transfer.getOriginalFilename().isBlank())
                ? transfer.getOriginalFilename()
                : "package";
        if (!zipName.toLowerCase().endsWith(".zip")) {
            zipName += ".zip";
        }

        response.setContentType("application/zip");
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(zipName, StandardCharsets.UTF_8)
                .build();
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        transferService.streamZipArchive(transferId, downloadToken, response.getOutputStream(), httpRequest);
    }

    @DeleteMapping("/api/v1/receive/{transferId}")
    @Operation(summary = "Abandon receive session")
    public ResponseEntity<Void> abandonReceive(@PathVariable String transferId) {
        return ResponseEntity.noContent().build();
    }
}
