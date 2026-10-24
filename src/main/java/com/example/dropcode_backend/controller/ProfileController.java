package com.example.dropcode_backend.controller;

import com.example.dropcode_backend.dto.BillingDtos;
import com.example.dropcode_backend.dto.ProfileDtos;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.exception.ApiException;
import com.example.dropcode_backend.repository.FileTransferRepository;
import com.example.dropcode_backend.repository.TransferRecipientRepository;
import com.example.dropcode_backend.repository.UserRepository;
import com.example.dropcode_backend.service.SubscriptionService;
import com.example.dropcode_backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "User profile management and transfer volume metrics")
public class ProfileController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final FileTransferRepository transferRepository;
    private final TransferRecipientRepository recipientRepository;

    @GetMapping({"/api/v1/profile", "/api/v1/users/me"})
    @Operation(summary = "Get user profile, unique @username, and transfer statistics")
    public ResponseEntity<ProfileDtos.ProfileResponse> getProfile() {
        UserEntity user = userService.getRequiredCurrentUser();
        BillingDtos.EntitlementDto entitlement = subscriptionService.getCurrentEntitlement();

        long sentCount = transferRepository.countBySenderUserId(user.getId());
        long receivedCount = recipientRepository.countByReceiverUserId(user.getId());
        long totalSentBytes = transferRepository.sumSentBytesByUserId(user.getId());
        long totalReceivedBytes = recipientRepository.sumReceivedBytesByUserId(user.getId());

        ProfileDtos.UserStatsDto stats = ProfileDtos.UserStatsDto.builder()
                .sentCount(sentCount)
                .receivedCount(receivedCount)
                .totalSentBytes(totalSentBytes)
                .totalReceivedBytes(totalReceivedBytes)
                .build();

        ProfileDtos.ProfileResponse response = ProfileDtos.ProfileResponse.builder()
                .uid(user.getFirebaseUid())
                .email(user.getEmail() != null ? user.getEmail() : "")
                .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .username(user.getUsername() != null ? user.getUsername() : "")
                .emailVerified(true)
                .createdAt(user.getCreatedAt().toString())
                .entitlement(entitlement)
                .stats(stats)
                .build();

        return ResponseEntity.ok(response);
    }

    @RequestMapping(
            value = {"/api/v1/profile", "/api/v1/users/me"},
            method = {RequestMethod.PUT, RequestMethod.PATCH}
    )
    @Operation(summary = "Update display name or username")
    public ResponseEntity<ProfileDtos.ProfileResponse> updateProfile(@RequestBody ProfileDtos.UpdateProfileRequest request) {
        UserEntity user = userService.getRequiredCurrentUser();

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            String newUsername = request.getUsername().trim().toLowerCase();
            if (!newUsername.equals(user.getUsername())) {
                if (userRepository.existsByUsername(newUsername)) {
                    throw new ApiException("USERNAME_TAKEN", "This username is already taken", HttpStatus.CONFLICT);
                }
                user.setUsername(newUsername);
            }
        }

        if (request.getDisplayName() != null && !request.getDisplayName().trim().isEmpty()) {
            user.setDisplayName(request.getDisplayName().trim());
        }

        userRepository.save(user);
        return getProfile();
    }
}
