package com.example.dropcode_backend;

import com.example.dropcode_backend.dto.QuoteDtos;
import com.example.dropcode_backend.dto.TransferDtos;
import com.example.dropcode_backend.entity.FileTransferEntity;
import com.example.dropcode_backend.entity.TransferItemEntity;
import com.example.dropcode_backend.entity.TransferRecipientEntity;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.exception.ApiException;
import com.example.dropcode_backend.repository.*;
import com.example.dropcode_backend.service.AuditService;
import com.example.dropcode_backend.service.TransferService;
import com.example.dropcode_backend.service.UserService;
import com.example.dropcode_backend.storage.StorageService;
import com.example.dropcode_backend.util.CryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferWorkflowTest {

    @Mock private FileTransferRepository transferRepository;
    @Mock private TransferItemRepository itemRepository;
    @Mock private TransferRecipientRepository recipientRepository;
    @Mock private TransferEventRepository eventRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserService userService;
    @Mock private StorageService storageService;
    @Mock private AuditService auditService;

    private TransferService transferService;

    private final String PEPPER = "test-pepper-string-32-chars-minimum-length";

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                transferRepository,
                itemRepository,
                recipientRepository,
                eventRepository,
                subscriptionRepository,
                userService,
                storageService,
                auditService
        );
        ReflectionTestUtils.setField(transferService, "codePepper", PEPPER);
        ReflectionTestUtils.setField(transferService, "codeTtlSeconds", 300);
        ReflectionTestUtils.setField(transferService, "downloadWindowSeconds", 900);
        ReflectionTestUtils.setField(transferService, "maxCodeAttempts", 5);
        ReflectionTestUtils.setField(transferService, "freeLimitBytes", 500_000_000L);
        ReflectionTestUtils.setField(transferService, "standardLimitBytes", 5_000_000_000L);
        ReflectionTestUtils.setField(transferService, "maxTransferBytes", 10_000_000_000L);
    }

    @Test
    @DisplayName("Create transfer generates ID and item mappings")
    void testCreateTransfer() {
        when(userService.getCurrentUser()).thenReturn(Optional.empty());

        TransferDtos.CreateTransferRequest req = TransferDtos.CreateTransferRequest.builder()
                .hasFolder(false)
                .senderName("Alice")
                .items(List.of(
                        QuoteDtos.QuoteItemDto.builder().name("file1.txt").size(100).path("file1.txt").type("text/plain").build(),
                        QuoteDtos.QuoteItemDto.builder().name("file2.png").size(200).path("file2.png").type("image/png").build()
                ))
                .build();

        TransferDtos.CreateTransferResponse res = transferService.createTransfer(req, null);

        assertNotNull(res.getTransferId());
        assertEquals(2, res.getItems().size());
        verify(transferRepository).save(any(FileTransferEntity.class));
        verify(itemRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Sender completing transfer receives 8-char code and hashed transfer record is updated")
    void testCompleteTransfer() {
        FileTransferEntity transfer = FileTransferEntity.builder()
                .id("tr_test_123")
                .transferStatus("UPLOADING")
                .build();

        when(transferRepository.findById("tr_test_123")).thenReturn(Optional.of(transfer));

        TransferDtos.CompleteTransferResponse res = transferService.completeTransfer("tr_test_123", null);

        assertNotNull(res.getCode());
        assertEquals(8, res.getCode().length());
        assertEquals("ready", res.getState());
        assertEquals("READY", transfer.getTransferStatus());
        assertNotNull(transfer.getTransferCodeHash());
    }

    @Test
    @DisplayName("Sender is prevented from claiming their own transfer code (403 OWN_TRANSFER)")
    void testSenderCannotClaimOwnTransfer() {
        UserEntity sender = UserEntity.builder().id(99L).build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(sender));

        String rawCode = "ABCDEFGH";
        String codeHash = CryptoUtils.hmacSha256(rawCode, PEPPER);

        FileTransferEntity transfer = FileTransferEntity.builder()
                .id("tr_own_456")
                .senderUser(sender)
                .transferCodeHash(codeHash)
                .transferStatus("READY")
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(transferRepository.findByTransferCodeHash(codeHash)).thenReturn(Optional.of(transfer));

        ApiException ex = assertThrows(ApiException.class, () -> transferService.verifyCode(rawCode, null));
        assertEquals("OWN_TRANSFER", ex.getCode());
    }

    @Test
    @DisplayName("Expired code is rejected with 410 Gone")
    void testExpiredCodeRejected() {
        String rawCode = "EXPIRED8";
        String codeHash = CryptoUtils.hmacSha256(rawCode, PEPPER);

        FileTransferEntity transfer = FileTransferEntity.builder()
                .id("tr_expired_789")
                .transferCodeHash(codeHash)
                .transferStatus("EXPIRED")
                .expiresAt(Instant.now().minusSeconds(10))
                .build();

        when(transferRepository.findByTransferCodeHash(codeHash)).thenReturn(Optional.of(transfer));

        ApiException ex = assertThrows(ApiException.class, () -> transferService.verifyCode(rawCode, null));
        assertEquals("INVALID_OR_EXPIRED_CODE", ex.getCode());
    }

    @Test
    @DisplayName("Cancelling transfer triggers immediate physical storage deletion")
    void testCancelTransferDeletesStorage() {
        FileTransferEntity transfer = FileTransferEntity.builder()
                .id("tr_cancel_111")
                .transferStatus("READY")
                .build();

        when(transferRepository.findById("tr_cancel_111")).thenReturn(Optional.of(transfer));

        transferService.cancelTransfer("tr_cancel_111", null);

        assertEquals("CANCELLED", transfer.getTransferStatus());
        assertNotNull(transfer.getDeletedAt());
        verify(storageService).deleteTransferPayloads("tr_cancel_111");
    }

    @Test
    @DisplayName("Single item download sets original filename, extension, and content type in response headers")
    void testStreamSingleItemPreservesOriginalFilenameAndContentType() throws Exception {
        String transferId = "tr_down_123";
        String itemId = "it_3a79dd3aef22";
        String rawToken = "test-download-token-123";
        String tokenHash = CryptoUtils.sha256Hex(rawToken);

        TransferRecipientEntity recipient = TransferRecipientEntity.builder()
                .downloadTokenHash(tokenHash)
                .downloadTokenExpiresAt(Instant.now().plusSeconds(600))
                .build();

        TransferItemEntity item = TransferItemEntity.builder()
                .id(itemId)
                .filename("financial_report.pdf")
                .relativePath("documents/financial_report.pdf")
                .contentType("application/pdf")
                .fileSizeBytes(2374322L)
                .isDownloaded(false)
                .build();

        when(recipientRepository.findByTransferIdAndDownloadTokenHash(transferId, tokenHash))
                .thenReturn(Optional.of(recipient));
        when(itemRepository.findByTransferIdAndId(transferId, itemId))
                .thenReturn(Optional.of(item));

        MockHttpServletResponse response = new MockHttpServletResponse();
        transferService.streamSingleItem(transferId, itemId, rawToken, response, null);

        assertEquals("application/pdf", response.getContentType());
        assertNotNull(response.getHeader("Content-Disposition"));
        assertTrue(response.getHeader("Content-Disposition").contains("financial_report.pdf"));
        assertEquals("2374322", response.getHeader("Content-Length"));
        assertTrue(item.getIsDownloaded());
        verify(storageService).readDecryptedItem(eq(transferId), eq(itemId), any());
    }
}
