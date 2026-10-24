package com.example.dropcode_backend.service;

import com.example.dropcode_backend.dto.HistoryDtos;
import com.example.dropcode_backend.dto.QuoteDtos;
import com.example.dropcode_backend.dto.TransferDtos;
import com.example.dropcode_backend.entity.*;
import com.example.dropcode_backend.exception.ApiException;
import com.example.dropcode_backend.repository.*;
import com.example.dropcode_backend.storage.StorageService;
import com.example.dropcode_backend.util.CryptoUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final FileTransferRepository transferRepository;
    private final TransferItemRepository itemRepository;
    private final TransferRecipientRepository recipientRepository;
    private final TransferEventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserService userService;
    private final StorageService storageService;
    private final AuditService auditService;

    @Value("${dropcode.security.code-pepper:dropcode-secure-code-pepper-key-minimum-32-chars-long}")
    private String codePepper;

    @Value("${dropcode.limits.code-ttl-seconds:300}")
    private int codeTtlSeconds;

    @Value("${dropcode.limits.download-window-seconds:900}")
    private int downloadWindowSeconds;

    @Value("${dropcode.limits.max-code-attempts:5}")
    private int maxCodeAttempts;

    @Value("${dropcode.limits.free-limit-bytes:500000000}")
    private long freeLimitBytes;

    @Value("${dropcode.limits.standard-limit-bytes:5000000000}")
    private long standardLimitBytes;

    @Value("${dropcode.limits.max-transfer-bytes:10000000000}")
    private long maxTransferBytes;

    private static final Set<String> EXECUTABLE_EXTENSIONS = Set.of(
            "exe", "msi", "dll", "bat", "cmd", "com", "scr", "ps1", "vbs", "sh"
    );

    public QuoteDtos.QuoteResponse quote(QuoteDtos.QuoteRequest request) {
        long totalBytes = 0;

        if (request.getItems() != null) {
            for (QuoteDtos.QuoteItemDto it : request.getItems()) {
                totalBytes += it.getSize();
            }
        }

        if (totalBytes > maxTransferBytes) {
            throw new ApiException("TRANSFER_TOO_LARGE", "Transfer exceeds maximum limit of 10 GB", HttpStatus.PAYLOAD_TOO_LARGE);
        }

        return QuoteDtos.QuoteResponse.builder()
                .allowed(true)
                .tier("free")
                .pricePaise(0)
                .requirements(List.of())
                .totalBytes(totalBytes)
                .build();
    }

    @Transactional
    public TransferDtos.CreateTransferResponse createTransfer(TransferDtos.CreateTransferRequest request, HttpServletRequest httpRequest) {
        QuoteDtos.QuoteResponse q = quote(QuoteDtos.QuoteRequest.builder()
                .items(request.getItems())
                .hasFolder(request.isHasFolder())
                .build());

        Optional<UserEntity> currentUser = userService.getCurrentUser();
        String transferId = "tr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        String originalName = "package";
        String contentType = "application/octet-stream";
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            QuoteDtos.QuoteItemDto firstItem = request.getItems().get(0);
            if (request.isHasFolder() && firstItem.getPath() != null && firstItem.getPath().replace('\\', '/').contains("/")) {
                String cleanPath = firstItem.getPath().replace('\\', '/').replaceAll("^/+", "");
                int slashIdx = cleanPath.indexOf('/');
                originalName = slashIdx > 0 ? cleanPath.substring(0, slashIdx) : firstItem.getName();
            } else {
                originalName = firstItem.getName();
            }
            contentType = firstItem.getType();
        }

        String senderName = request.getSenderName();
        if (senderName == null || senderName.trim().isEmpty()) {
            senderName = currentUser.map(UserEntity::getDisplayName).orElse("Anonymous Sender");
        }

        FileTransferEntity transfer = FileTransferEntity.builder()
                .id(transferId)
                .senderUser(currentUser.orElse(null))
                .senderDisplayName(senderName)
                .originalFilename(originalName)
                .fileSizeBytes(q.getTotalBytes())
                .contentType(contentType)
                .hasFolder(request.isHasFolder())
                .transferStatus("CREATED")
                .expiresAt(Instant.now().plus(codeTtlSeconds * 2L, ChronoUnit.SECONDS))
                .build();

        transferRepository.save(transfer);

        List<TransferDtos.ItemMapping> mappings = new ArrayList<>();
        if (request.getItems() != null) {
            for (int i = 0; i < request.getItems().size(); i++) {
                QuoteDtos.QuoteItemDto itemDto = request.getItems().get(i);
                String itemId = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

                TransferItemEntity item = TransferItemEntity.builder()
                        .id(itemId)
                        .transfer(transfer)
                        .itemIndex(i)
                        .filename(itemDto.getName())
                        .relativePath(itemDto.getPath() != null && !itemDto.getPath().isEmpty() ? itemDto.getPath() : itemDto.getName())
                        .fileSizeBytes(itemDto.getSize())
                        .contentType(itemDto.getType())
                        .isDownloaded(false)
                        .build();

                itemRepository.save(item);
                mappings.add(TransferDtos.ItemMapping.builder().index(i).itemId(itemId).build());
            }
        }

        recordEvent(transfer, currentUser.orElse(null), "TRANSFER_CREATED", "SUCCESS", "Items: " + mappings.size());
        auditService.recordAccess(currentUser.orElse(null), transfer, "CREATE_TRANSFER", httpRequest);

        return TransferDtos.CreateTransferResponse.builder()
                .transferId(transferId)
                .items(mappings)
                .build();
    }

    public void uploadItem(String transferId, String itemId, InputStream inputStream, HttpServletRequest httpRequest) throws IOException {
        FileTransferEntity transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Transfer not found", HttpStatus.NOT_FOUND));

        if (!"CREATED".equalsIgnoreCase(transfer.getTransferStatus()) && !"UPLOADING".equalsIgnoreCase(transfer.getTransferStatus())) {
            throw new ApiException("INVALID_STATE", "Transfer is not accepting uploads", HttpStatus.CONFLICT);
        }

        TransferItemEntity item = itemRepository.findByTransferIdAndId(transferId, itemId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Transfer item not found", HttpStatus.NOT_FOUND));

        long plainBytes = storageService.saveItemStream(transferId, itemId, inputStream);
        log.info("Saved item {}: {} bytes", itemId, plainBytes);

        if (!"UPLOADING".equalsIgnoreCase(transfer.getTransferStatus())) {
            transfer.setTransferStatus("UPLOADING");
            transferRepository.save(transfer);
        }
    }

    @Transactional
    public TransferDtos.CompleteTransferResponse completeTransfer(String transferId, HttpServletRequest httpRequest) {
        FileTransferEntity transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Transfer not found", HttpStatus.NOT_FOUND));

        // Generate 8-char code
        String rawCode = CryptoUtils.generateShareCode(8);
        String codeHash = CryptoUtils.hmacSha256(rawCode, codePepper);

        Instant expiresAt = Instant.now().plus(codeTtlSeconds, ChronoUnit.SECONDS);
        transfer.setTransferCodeHash(codeHash);
        transfer.setCodePreview(rawCode.substring(0, 2) + "..." + rawCode.substring(6));
        transfer.setTransferStatus("READY");
        transfer.setExpiresAt(expiresAt);
        transfer.setCompletedAt(Instant.now());
        transferRepository.save(transfer);

        recordEvent(transfer, transfer.getSenderUser(), "TRANSFER_READY", "SUCCESS", "Code generated");
        auditService.recordAccess(transfer.getSenderUser(), transfer, "COMPLETE_TRANSFER", httpRequest);

        return TransferDtos.CompleteTransferResponse.builder()
                .code(rawCode)
                .expiresAt(expiresAt.toString())
                .state("ready")
                .build();
    }

    public TransferDtos.TransferStatusResponse getSenderStatus(String transferId) {
        FileTransferEntity transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Transfer not found", HttpStatus.NOT_FOUND));

        String state = transfer.getTransferStatus().toLowerCase();
        if ("ready".equals(state) || "waiting_for_receiver".equals(state)) {
            state = "ready";
        }

        return TransferDtos.TransferStatusResponse.builder()
                .state(state)
                .expiresAt(transfer.getExpiresAt().toString())
                .build();
    }

    @Transactional
    public void cancelTransfer(String transferId, HttpServletRequest httpRequest) {
        Optional<FileTransferEntity> opt = transferRepository.findById(transferId);
        if (opt.isPresent()) {
            FileTransferEntity transfer = opt.get();
            transfer.setTransferStatus("CANCELLED");
            transfer.setDeletedAt(Instant.now());
            transferRepository.save(transfer);

            storageService.deleteTransferPayloads(transferId);
            recordEvent(transfer, transfer.getSenderUser(), "TRANSFER_CANCELLED", "SUCCESS", "User cancelled");
            auditService.recordAccess(transfer.getSenderUser(), transfer, "CANCEL_TRANSFER", httpRequest);
        }
    }

    @Transactional
    public TransferDtos.VerifyCodeResponse verifyCode(String rawCode, HttpServletRequest httpRequest) {
        String cleanCode = rawCode.replaceAll("[^A-Z0-9]", "").toUpperCase();
        String codeHash = CryptoUtils.hmacSha256(cleanCode, codePepper);

        FileTransferEntity transfer = transferRepository.findByTransferCodeHash(codeHash)
                .orElseThrow(() -> new ApiException("INVALID_OR_EXPIRED_CODE", "Invalid or expired transfer code", HttpStatus.NOT_FOUND, Map.of("attemptsLeft", 3)));

        if (Instant.now().isAfter(transfer.getExpiresAt()) || "EXPIRED".equalsIgnoreCase(transfer.getTransferStatus()) || "CANCELLED".equalsIgnoreCase(transfer.getTransferStatus()) || "DELIVERED".equalsIgnoreCase(transfer.getTransferStatus())) {
            throw new ApiException("INVALID_OR_EXPIRED_CODE", "This code has expired or is no longer available", HttpStatus.GONE);
        }

        Optional<UserEntity> currentUser = userService.getCurrentUser();
        if (currentUser.isPresent() && transfer.getSenderUser() != null &&
                currentUser.get().getId().equals(transfer.getSenderUser().getId())) {
            throw new ApiException("OWN_TRANSFER", "You cannot receive a transfer you are sending", HttpStatus.FORBIDDEN);
        }

        // Generate download token (15 minute window)
        String rawToken = CryptoUtils.generateSecureToken(32);
        String tokenHash = CryptoUtils.sha256Hex(rawToken);
        Instant tokenExpires = Instant.now().plus(downloadWindowSeconds, ChronoUnit.SECONDS);

        TransferRecipientEntity recipient = TransferRecipientEntity.builder()
                .transfer(transfer)
                .receiverUser(currentUser.orElse(null))
                .receiverEmail(currentUser.map(UserEntity::getEmail).orElse(null))
                .receiverDisplayName(currentUser.map(UserEntity::getDisplayName).orElse("Anonymous Receiver"))
                .recipientStatus("CLAIMED")
                .downloadTokenHash(tokenHash)
                .downloadTokenExpiresAt(tokenExpires)
                .firstAccessedAt(Instant.now())
                .build();
        recipientRepository.save(recipient);

        transfer.setTransferStatus("CLAIMED");
        transferRepository.save(transfer);

        recordEvent(transfer, currentUser.orElse(null), "CODE_VERIFIED", "SUCCESS", "Token issued");
        auditService.recordAccess(currentUser.orElse(null), transfer, "VERIFY_CODE", httpRequest);

        List<TransferItemEntity> items = itemRepository.findByTransferIdOrderByItemIndexAsc(transfer.getId());
        List<TransferDtos.ReceiverItemDto> receiverItems = items.stream().map(it -> TransferDtos.ReceiverItemDto.builder()
                .id(it.getId())
                .name(it.getFilename())
                .size(it.getFileSizeBytes())
                .path(it.getRelativePath())
                .type(it.getContentType())
                .build()).toList();

        return TransferDtos.VerifyCodeResponse.builder()
                .transferId(transfer.getId())
                .sender(TransferDtos.SenderInfoDto.builder().displayName(transfer.getSenderDisplayName()).build())
                .items(receiverItems)
                .totalSize(transfer.getFileSizeBytes())
                .downloadToken(rawToken)
                .downloadExpiresAt(tokenExpires.toString())
                .originalFilename(transfer.getOriginalFilename())
                .hasFolder(Boolean.TRUE.equals(transfer.getHasFolder()))
                .build();
    }

    public TransferDtos.ReceiverStatusResponse getReceiverStatus(String transferId) {
        FileTransferEntity transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Transfer not found", HttpStatus.NOT_FOUND));

        List<TransferItemEntity> items = itemRepository.findByTransferIdOrderByItemIndexAsc(transferId);
        List<TransferDtos.ReceiverItemStatusDto> itemStatuses = items.stream().map(it -> TransferDtos.ReceiverItemStatusDto.builder()
                .id(it.getId())
                .downloaded(Boolean.TRUE.equals(it.getIsDownloaded()))
                .build()).toList();

        return TransferDtos.ReceiverStatusResponse.builder()
                .state(transfer.getTransferStatus().toLowerCase())
                .items(itemStatuses)
                .downloadExpiresAt(transfer.getExpiresAt().toString())
                .build();
    }

    @Transactional
    public void streamSingleItem(String transferId, String itemId, String downloadToken, HttpServletResponse response, HttpServletRequest httpRequest) throws IOException {
        validateDownloadToken(transferId, downloadToken);

        TransferItemEntity item = itemRepository.findByTransferIdAndId(transferId, itemId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Item not found", HttpStatus.NOT_FOUND));

        String filename = item.getFilename();
        if (filename == null || filename.isBlank()) {
            filename = itemId;
        }

        // Determine appropriate MIME Content-Type
        String contentType = item.getContentType();
        if (contentType == null || contentType.isBlank() || MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(contentType)) {
            contentType = MediaTypeFactory.getMediaType(filename)
                    .map(MediaType::toString)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        }
        response.setContentType(contentType);

        // Set RFC-compliant Content-Disposition header with UTF-8 filename fallback
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());

        if (item.getFileSizeBytes() != null && item.getFileSizeBytes() > 0) {
            response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(item.getFileSizeBytes()));
        }

        storageService.readDecryptedItem(transferId, itemId, response.getOutputStream());
        item.setIsDownloaded(true);
        itemRepository.save(item);

        checkDeliveryCompletion(transferId, httpRequest);
    }

    @Transactional
    public void streamSingleItem(String transferId, String itemId, String downloadToken, OutputStream outputStream, HttpServletRequest httpRequest) throws IOException {
        validateDownloadToken(transferId, downloadToken);

        TransferItemEntity item = itemRepository.findByTransferIdAndId(transferId, itemId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Item not found", HttpStatus.NOT_FOUND));

        storageService.readDecryptedItem(transferId, itemId, outputStream);
        item.setIsDownloaded(true);
        itemRepository.save(item);

        checkDeliveryCompletion(transferId, httpRequest);
    }

    @Transactional
    public void streamZipArchive(String transferId, String downloadToken, OutputStream outputStream, HttpServletRequest httpRequest) throws IOException {
        validateDownloadToken(transferId, downloadToken);

        List<TransferItemEntity> items = itemRepository.findByTransferIdOrderByItemIndexAsc(transferId);
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            for (TransferItemEntity it : items) {
                String entryPath = it.getRelativePath();
                if (entryPath == null || entryPath.isBlank()) {
                    entryPath = it.getFilename();
                }
                if (entryPath == null || entryPath.isBlank()) {
                    entryPath = it.getId();
                }
                // Normalize slashes and trim leading slashes for cross-platform zip extraction
                entryPath = entryPath.replace('\\', '/').replaceAll("^/+", "");
                if (entryPath.isBlank()) {
                    entryPath = it.getFilename() != null ? it.getFilename() : it.getId();
                }

                ZipEntry entry = new ZipEntry(entryPath);
                if (it.getFileSizeBytes() != null && it.getFileSizeBytes() >= 0) {
                    entry.setSize(it.getFileSizeBytes());
                }
                zipOut.putNextEntry(entry);
                storageService.readDecryptedItem(transferId, it.getId(), zipOut);
                zipOut.closeEntry();
                it.setIsDownloaded(true);
                itemRepository.save(it);
            }
        }

        checkDeliveryCompletion(transferId, httpRequest);
    }

    private void validateDownloadToken(String transferId, String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            throw new ApiException("FORBIDDEN", "Download token is required", HttpStatus.FORBIDDEN);
        }
        String tokenHash = CryptoUtils.sha256Hex(rawToken);
        TransferRecipientEntity recipient = recipientRepository.findByTransferIdAndDownloadTokenHash(transferId, tokenHash)
                .orElseThrow(() -> new ApiException("FORBIDDEN", "Invalid download token", HttpStatus.FORBIDDEN));

        if (recipient.getDownloadTokenExpiresAt() != null && Instant.now().isAfter(recipient.getDownloadTokenExpiresAt())) {
            throw new ApiException("GONE", "Download token has expired", HttpStatus.GONE);
        }
    }

    private void checkDeliveryCompletion(String transferId, HttpServletRequest httpRequest) {
        FileTransferEntity transfer = transferRepository.findById(transferId).orElse(null);
        if (transfer == null) return;

        List<TransferItemEntity> items = itemRepository.findByTransferIdOrderByItemIndexAsc(transferId);
        boolean allDownloaded = items.stream().allMatch(it -> Boolean.TRUE.equals(it.getIsDownloaded()));

        if (allDownloaded) {
            transfer.setTransferStatus("DELIVERED");
            transfer.setDeletedAt(Instant.now());
            transferRepository.save(transfer);

            List<TransferRecipientEntity> recipients = recipientRepository.findByTransferId(transferId);
            for (TransferRecipientEntity r : recipients) {
                r.setRecipientStatus("DELIVERED");
                r.setDownloadedAt(Instant.now());
                recipientRepository.save(r);
            }

            // Zero-trace immediate purge
            storageService.deleteTransferPayloads(transferId);
            recordEvent(transfer, null, "TRANSFER_DELIVERED", "SUCCESS", "All files retrieved. Staging purged.");
            auditService.recordAccess(null, transfer, "DOWNLOAD_COMPLETE", httpRequest);
        }
    }

    @Scheduled(fixedRate = 60000) // Runs every minute
    @Transactional
    public void cleanupExpiredTransfers() {
        List<FileTransferEntity> expired = transferRepository.findExpiredTransfers(Instant.now());
        for (FileTransferEntity t : expired) {
            log.info("Expiring transfer: id={}, status={}", t.getId(), t.getTransferStatus());
            t.setTransferStatus("EXPIRED");
            t.setDeletedAt(Instant.now());
            transferRepository.save(t);
            storageService.deleteTransferPayloads(t.getId());
            recordEvent(t, null, "TRANSFER_EXPIRED", "SUCCESS", "TTL expired. Ephemeral payload purged.");
        }
    }

    public FileTransferEntity getTransferEntity(String transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Transfer not found", HttpStatus.NOT_FOUND));
    }

    private void recordEvent(FileTransferEntity transfer, UserEntity user, String type, String status, String meta) {
        try {
            TransferEventEntity ev = TransferEventEntity.builder()
                    .transfer(transfer)
                    .actorUser(user)
                    .eventType(type)
                    .eventStatus(status)
                    .eventTimestamp(Instant.now())
                    .sanitizedMetadata(meta)
                    .build();
            eventRepository.save(ev);
        } catch (Exception e) {
            log.warn("Failed to record transfer event: {}", e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
