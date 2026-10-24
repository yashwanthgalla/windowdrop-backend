package com.example.dropcode_backend;

import com.example.dropcode_backend.dto.HistoryDtos;
import com.example.dropcode_backend.entity.*;
import com.example.dropcode_backend.repository.*;
import com.example.dropcode_backend.service.AuditService;
import com.example.dropcode_backend.service.HistoryService;
import com.example.dropcode_backend.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSyncAndHistoryPrivacyTest {

    @Mock private UserRepository userRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private FileTransferRepository transferRepository;
    @Mock private TransferItemRepository itemRepository;
    @Mock private TransferRecipientRepository recipientRepository;
    @Mock private AccessAuditRepository accessAuditRepository;

    @Test
    @DisplayName("New Firebase user is automatically synchronized with default Free subscription")
    void testSyncNewFirebaseUser() {
        UserService userService = new UserService(userRepository, planRepository, subscriptionRepository);

        when(userRepository.findByFirebaseUid("fb_uid_123")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);

        UserEntity savedUser = UserEntity.builder()
                .id(1L)
                .firebaseUid("fb_uid_123")
                .email("test@example.com")
                .displayName("Test User")
                .username("test")
                .build();
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        SubscriptionPlanEntity freePlan = SubscriptionPlanEntity.builder()
                .id(1L)
                .planCode("free")
                .pricePaise(0L)
                .build();
        when(planRepository.findByPlanCode("free")).thenReturn(Optional.of(freePlan));

        UserEntity user = userService.syncFirebaseUser("fb_uid_123", "test@example.com", "Test User", true);

        assertNotNull(user);
        assertEquals("fb_uid_123", user.getFirebaseUid());
        verify(subscriptionRepository).save(any(SubscriptionEntity.class));
    }

    @Test
    @DisplayName("User history is strictly isolated and displays metadata even after file deletion")
    void testHistoryAfterFileDeleted() {
        UserService userService = mock(UserService.class);
        UserEntity user = UserEntity.builder().id(5L).build();
        when(userService.getRequiredCurrentUser()).thenReturn(user);

        HistoryService historyService = new HistoryService(transferRepository, itemRepository, recipientRepository, userService);

        // Transfer with deleted physical file (deleted_at is set)
        FileTransferEntity deletedTransfer = FileTransferEntity.builder()
                .id("tr_deleted_555")
                .senderUser(user)
                .senderDisplayName("User 5")
                .transferStatus("DELIVERED")
                .fileSizeBytes(150_000_000L)
                .createdAt(Instant.now().minusSeconds(3600))
                .deletedAt(Instant.now().minusSeconds(1800)) // physical file purged
                .build();

        when(transferRepository.findSentHistory(eq(5L), any(Pageable.class)))
                .thenReturn(List.of(deletedTransfer));

        when(itemRepository.findByTransferIdOrderByItemIndexAsc("tr_deleted_555"))
                .thenReturn(List.of(TransferItemEntity.builder()
                        .filename("report.pdf")
                        .relativePath("report.pdf")
                        .fileSizeBytes(150_000_000L)
                        .contentType("application/pdf")
                        .build()));

        HistoryDtos.HistoryResponse history = historyService.getHistory("sent", null, 20);

        assertNotNull(history);
        assertEquals(1, history.getEntries().size());
        HistoryDtos.HistoryEntryDto entry = history.getEntries().get(0);
        assertEquals("tr_deleted_555", entry.getId());
        assertEquals("delivered", entry.getState());
        assertEquals(150_000_000L, entry.getTotalBytes());
        assertNotNull(entry.getDeliveredAt());
        // Verify no raw IP address is exposed
        assertFalse(entry.toString().contains("192.168.") && assertFalseIp(entry.toString()));
    }

    private boolean assertFalseIp(String content) {
        return content.matches(".*\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b.*");
    }

    @Test
    @DisplayName("Audit service encrypts client IP at rest with AES-256-GCM")
    void testAuditServiceIpEncryption() {
        AuditService auditService = new AuditService(accessAuditRepository);
        ReflectionTestUtils.setField(auditService, "ipEncryptionKey", "12345678901234567890123456789012");
        ReflectionTestUtils.setField(auditService, "auditRetentionDays", 90);

        auditService.recordAccess(null, null, "TEST_ACCESS", null);

        ArgumentCaptor<AccessAuditEntity> captor = ArgumentCaptor.forClass(AccessAuditEntity.class);
        verify(accessAuditRepository).save(captor.capture());
        AccessAuditEntity audit = captor.getValue();

        assertNotNull(audit.getEncryptedIpAddress());
        assertNotEquals("127.0.0.1", audit.getEncryptedIpAddress());
        assertNotNull(audit.getRetentionExpiresAt());
    }
}
