package com.example.dropcode_backend.service;

import com.example.dropcode_backend.entity.SubscriptionEntity;
import com.example.dropcode_backend.entity.SubscriptionPlanEntity;
import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.exception.ApiException;
import com.example.dropcode_backend.repository.SubscriptionPlanRepository;
import com.example.dropcode_backend.repository.SubscriptionRepository;
import com.example.dropcode_backend.repository.UserRepository;
import com.example.dropcode_backend.security.FirebaseUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public UserEntity syncFirebaseUser(String uid, String email, String displayName, boolean emailVerified) {
        Optional<UserEntity> existing = userRepository.findByFirebaseUid(uid);
        if (existing.isPresent()) {
            UserEntity user = existing.get();
            boolean updated = false;
            if (displayName != null && !displayName.trim().isEmpty() && !displayName.equals(user.getDisplayName())) {
                user.setDisplayName(displayName.trim());
                updated = true;
            }
            if (email != null && !email.trim().isEmpty() && !email.equals(user.getEmail())) {
                user.setEmail(email.trim());
                updated = true;
            }
            user.setLastLoginAt(Instant.now());
            return userRepository.save(user);
        }

        // Generate clean unique username
        String baseUsername = generateBaseUsername(email, displayName);
        String username = baseUsername;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + suffix;
            suffix++;
        }

        UserEntity newUser = UserEntity.builder()
                .firebaseUid(uid)
                .email(email != null ? email.trim() : null)
                .displayName(displayName != null && !displayName.trim().isEmpty() ? displayName.trim() : username)
                .username(username)
                .accountStatus("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastLoginAt(Instant.now())
                .build();

        newUser = userRepository.save(newUser);
        log.info("Created new synchronized user: id={}, uid={}, username={}", newUser.getId(), uid, username);

        // Assign default Free subscription
        SubscriptionPlanEntity freePlan = subscriptionPlanRepository.findByPlanCode("free")
                .orElseGet(() -> subscriptionPlanRepository.save(SubscriptionPlanEntity.builder()
                        .planCode("free")
                        .planName("Free Plan")
                        .pricePaise(0L)
                        .currency("INR")
                        .billingIntervalDays(365)
                        .maxFileSizeBytes(500000000L)
                        .isActive(true)
                        .build()));

        SubscriptionEntity freeSub = SubscriptionEntity.builder()
                .user(newUser)
                .plan(freePlan)
                .subscriptionStatus("ACTIVE")
                .startedAt(Instant.now())
                .expiresAt(Instant.now().plus(3650, ChronoUnit.DAYS)) // Long-lived free tier
                .build();
        subscriptionRepository.save(freeSub);

        return newUser;
    }

    private String generateBaseUsername(String email, String displayName) {
        if (displayName != null && !displayName.trim().isEmpty()) {
            String sanitized = displayName.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
            if (sanitized.length() >= 3) {
                return sanitized.substring(0, Math.min(sanitized.length(), 15));
            }
        }
        if (email != null && email.contains("@")) {
            String prefix = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
            if (prefix.length() >= 3) {
                return prefix.substring(0, Math.min(prefix.length(), 15));
            }
        }
        return "user_" + UUID.randomUUID().toString().substring(0, 6);
    }

    public Optional<UserEntity> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof FirebaseUserDetails userDetails) {
            return userRepository.findByFirebaseUid(userDetails.getFirebaseUid());
        }
        return Optional.empty();
    }

    public UserEntity getRequiredCurrentUser() {
        return getCurrentUser().orElseThrow(() ->
                new ApiException("LOGIN_REQUIRED", "You must be signed in to perform this action", HttpStatus.UNAUTHORIZED));
    }
}
