package com.example.dropcode_backend.security;

import com.example.dropcode_backend.entity.UserEntity;
import com.example.dropcode_backend.service.UserService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private final UserService userService;

    public FirebaseAuthenticationFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty()) {
                try {
                    String uid = null;
                    String email = null;
                    String name = null;
                    boolean emailVerified = false;

                    // Support dev mode / simulated token for offline testing
                    if (token.startsWith("demo_token_") || token.startsWith("mock_token_")) {
                        uid = token.replace("demo_token_", "uid_").replace("mock_token_", "uid_");
                        email = uid + "@dropcode.local";
                        name = "Demo User";
                        emailVerified = true;
                    } else if (token.startsWith("dev_token_")) {
                        String payload = token.substring("dev_token_".length());
                        String[] parts = payload.split(":");
                        uid = parts[0];
                        try {
                            email = parts.length > 1 ? new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8) : uid + "@dropcode.local";
                            name = parts.length > 2 ? new String(java.util.Base64.getUrlDecoder().decode(parts[2]), java.nio.charset.StandardCharsets.UTF_8) : "User";
                        } catch (Exception b64Ex) {
                            email = uid + "@dropcode.local";
                            name = "User";
                        }
                        emailVerified = true;
                    } else if (!FirebaseApp.getApps().isEmpty()) {
                        try {
                            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
                            uid = decodedToken.getUid();
                            email = decodedToken.getEmail();
                            name = decodedToken.getName();
                            emailVerified = decodedToken.isEmailVerified();
                        } catch (Exception firebaseEx) {
                            log.debug("Firebase ID token verification failed: {}", firebaseEx.getMessage());
                        }
                    }

                    if (uid != null) {
                        UserEntity user = userService.syncFirebaseUser(uid, email, name, emailVerified);
                        FirebaseUserDetails userDetails = new FirebaseUserDetails(user, uid, email, name, emailVerified);
                        FirebaseAuthenticationToken authentication = new FirebaseAuthenticationToken(
                                userDetails, token, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception ex) {
                    log.error("Authentication processing error: {}", ex.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
