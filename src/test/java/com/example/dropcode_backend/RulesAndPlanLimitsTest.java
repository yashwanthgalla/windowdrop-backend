package com.example.dropcode_backend;

import com.example.dropcode_backend.dto.QuoteDtos;
import com.example.dropcode_backend.entity.SubscriptionEntity;
import com.example.dropcode_backend.entity.SubscriptionPlanEntity;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.exception.ApiException;
import com.example.dropcode_backend.repository.*;
import com.example.dropcode_backend.service.AuditService;
import com.example.dropcode_backend.service.TransferService;
import com.example.dropcode_backend.service.UserService;
import com.example.dropcode_backend.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RulesAndPlanLimitsTest {

    @Mock private FileTransferRepository transferRepository;
    @Mock private TransferItemRepository itemRepository;
    @Mock private TransferRecipientRepository recipientRepository;
    @Mock private TransferEventRepository eventRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserService userService;
    @Mock private StorageService storageService;
    @Mock private AuditService auditService;

    private TransferService transferService;

    private final long FREE_LIMIT = 500_000_000L;
    private final long PREMIUM_LIMIT = 5_000_000_000L;
    private final long PRO_LIMIT = 10_000_000_000L;

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
        ReflectionTestUtils.setField(transferService, "codePepper", "test-pepper-string-32-chars-minimum-length");
        ReflectionTestUtils.setField(transferService, "codeTtlSeconds", 300);
        ReflectionTestUtils.setField(transferService, "downloadWindowSeconds", 900);
        ReflectionTestUtils.setField(transferService, "maxCodeAttempts", 5);
        ReflectionTestUtils.setField(transferService, "freeLimitBytes", FREE_LIMIT);
        ReflectionTestUtils.setField(transferService, "standardLimitBytes", PREMIUM_LIMIT);
        ReflectionTestUtils.setField(transferService, "maxTransferBytes", PRO_LIMIT);
    }

    @Test
    @DisplayName("Free user is permitted exactly at 500 MB boundary")
    void testFreePlanBoundary() {
        when(userService.getCurrentUser()).thenReturn(Optional.empty());

        QuoteDtos.QuoteRequest req = QuoteDtos.QuoteRequest.builder()
                .hasFolder(false)
                .items(List.of(QuoteDtos.QuoteItemDto.builder()
                        .name("archive.zip")
                        .size(FREE_LIMIT)
                        .path("archive.zip")
                        .type("application/zip")
                        .build()))
                .build();

        QuoteDtos.QuoteResponse res = transferService.quote(req);
        assertTrue(res.isAllowed());
        assertEquals("free", res.getTier());
        assertEquals(0L, res.getPricePaise());
        assertTrue(res.getRequirements().isEmpty());
    }

    @Test
    @DisplayName("Free user is rejected at 500 MB + 1 byte boundary")
    void testFreePlanExceededByOneByte() {
        when(userService.getCurrentUser()).thenReturn(Optional.empty());

        QuoteDtos.QuoteRequest req = QuoteDtos.QuoteRequest.builder()
                .hasFolder(false)
                .items(List.of(QuoteDtos.QuoteItemDto.builder()
                        .name("large.iso")
                        .size(FREE_LIMIT + 1)
                        .path("large.iso")
                        .type("application/octet-stream")
                        .build()))
                .build();

        QuoteDtos.QuoteResponse res = transferService.quote(req);
        assertFalse(res.isAllowed());
        assertEquals("standard", res.getTier());
        assertEquals(4900L, res.getPricePaise());
        assertTrue(res.getRequirements().contains("PAYMENT_REQUIRED"));
    }

    @Test
    @DisplayName("Premium user is permitted up to 5 GB boundary")
    void testPremiumPlanBoundary() {
        UserEntity user = UserEntity.builder().id(101L).build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        SubscriptionPlanEntity premiumPlan = SubscriptionPlanEntity.builder()
                .planCode("premium")
                .maxFileSizeBytes(PREMIUM_LIMIT)
                .build();

        SubscriptionEntity sub = SubscriptionEntity.builder()
                .user(user)
                .plan(premiumPlan)
                .subscriptionStatus("ACTIVE")
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();

        when(subscriptionRepository.findHighestActiveSubscription(101L)).thenReturn(Optional.of(sub));

        QuoteDtos.QuoteRequest req = QuoteDtos.QuoteRequest.builder()
                .hasFolder(false)
                .items(List.of(QuoteDtos.QuoteItemDto.builder()
                        .name("video.mp4")
                        .size(PREMIUM_LIMIT)
                        .path("video.mp4")
                        .type("video/mp4")
                        .build()))
                .build();

        QuoteDtos.QuoteResponse res = transferService.quote(req);
        assertTrue(res.isAllowed());
        assertTrue(res.getRequirements().isEmpty());
    }

    @Test
    @DisplayName("Premium user is rejected at 5 GB + 1 byte boundary")
    void testPremiumPlanExceededByOneByte() {
        UserEntity user = UserEntity.builder().id(101L).build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        SubscriptionPlanEntity premiumPlan = SubscriptionPlanEntity.builder()
                .planCode("premium")
                .maxFileSizeBytes(PREMIUM_LIMIT)
                .build();

        SubscriptionEntity sub = SubscriptionEntity.builder()
                .user(user)
                .plan(premiumPlan)
                .subscriptionStatus("ACTIVE")
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();

        when(subscriptionRepository.findHighestActiveSubscription(101L)).thenReturn(Optional.of(sub));

        QuoteDtos.QuoteRequest req = QuoteDtos.QuoteRequest.builder()
                .hasFolder(false)
                .items(List.of(QuoteDtos.QuoteItemDto.builder()
                        .name("huge.zip")
                        .size(PREMIUM_LIMIT + 1)
                        .path("huge.zip")
                        .type("application/zip")
                        .build()))
                .build();

        QuoteDtos.QuoteResponse res = transferService.quote(req);
        assertFalse(res.isAllowed());
        assertEquals("large", res.getTier());
        assertEquals(7900L, res.getPricePaise());
        assertTrue(res.getRequirements().contains("PAYMENT_REQUIRED"));
    }

    @Test
    @DisplayName("Pro user is permitted up to 10 GB boundary")
    void testProPlanBoundary() {
        UserEntity user = UserEntity.builder().id(202L).build();
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        SubscriptionPlanEntity proPlan = SubscriptionPlanEntity.builder()
                .planCode("pro")
                .maxFileSizeBytes(PRO_LIMIT)
                .build();

        SubscriptionEntity sub = SubscriptionEntity.builder()
                .user(user)
                .plan(proPlan)
                .subscriptionStatus("ACTIVE")
                .expiresAt(Instant.now().plusSeconds(86400))
                .build();

        when(subscriptionRepository.findHighestActiveSubscription(202L)).thenReturn(Optional.of(sub));

        QuoteDtos.QuoteRequest req = QuoteDtos.QuoteRequest.builder()
                .hasFolder(false)
                .items(List.of(QuoteDtos.QuoteItemDto.builder()
                        .name("disk.img")
                        .size(PRO_LIMIT)
                        .path("disk.img")
                        .type("application/octet-stream")
                        .build()))
                .build();

        QuoteDtos.QuoteResponse res = transferService.quote(req);
        assertTrue(res.isAllowed());
    }

    @Test
    @DisplayName("Transfer exceeding 10 GB absolute maximum is rejected")
    void testExceedsMaxTransferBytes() {
        QuoteDtos.QuoteRequest req = QuoteDtos.QuoteRequest.builder()
                .hasFolder(false)
                .items(List.of(QuoteDtos.QuoteItemDto.builder()
                        .name("overflow.bin")
                        .size(PRO_LIMIT + 1)
                        .path("overflow.bin")
                        .type("application/octet-stream")
                        .build()))
                .build();

        assertThrows(ApiException.class, () -> transferService.quote(req));
    }

    @Test
    @DisplayName("Executable file requires paid plan")
    void testExecutableFileRequiresPaidPlan() {
        when(userService.getCurrentUser()).thenReturn(Optional.empty());

        QuoteDtos.QuoteRequest req = QuoteDtos.QuoteRequest.builder()
                .hasFolder(false)
                .items(List.of(QuoteDtos.QuoteItemDto.builder()
                        .name("installer.exe")
                        .size(10_000_000L)
                        .path("installer.exe")
                        .type("application/x-msdownload")
                        .build()))
                .build();

        QuoteDtos.QuoteResponse res = transferService.quote(req);
        assertFalse(res.isAllowed());
        assertTrue(res.getRequirements().contains("PLAN_REQUIRED_FOR_EXECUTABLES"));
    }

    @Test
    @DisplayName("Folder transfer strictly requires authenticated user")
    void testFolderTransferRequiresLogin() {
        when(userService.getCurrentUser()).thenReturn(Optional.empty());

        QuoteDtos.QuoteRequest req = QuoteDtos.QuoteRequest.builder()
                .hasFolder(true)
                .items(List.of(QuoteDtos.QuoteItemDto.builder()
                        .name("doc.pdf")
                        .size(100_000L)
                        .path("project/doc.pdf")
                        .type("application/pdf")
                        .build()))
                .build();

        QuoteDtos.QuoteResponse res = transferService.quote(req);
        assertFalse(res.isAllowed());
        assertTrue(res.getRequirements().contains("LOGIN_REQUIRED"));
    }
}
