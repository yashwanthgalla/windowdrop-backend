package com.example.dropcode_backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthenticationFilter, ObjectMapper objectMapper) {
        this.firebaseAuthenticationFilter = firebaseAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Value("${dropcode.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public system, docs, health
                        .requestMatchers("/actuator/**", "/healthz", "/readyz").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Public transfer & config endpoints
                        .requestMatchers(HttpMethod.GET, "/api/v1/config").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/billing/plans", "/api/v1/subscriptions/plans").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/transfers/quote").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/activity/heartbeat").permitAll()

                        // Ephemeral Transfers (guest or user creation/upload/complete/verify/download)
                        .requestMatchers(HttpMethod.POST, "/api/v1/transfers").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/transfers/*/items/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/transfers/*/complete").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/transfers/*/status").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/transfers/*").permitAll()

                        // Receive flow endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/receive/verify", "/api/v1/transfers/resolve-code").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/receive/*/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/receive/*/items/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/receive/*/zip").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/receive/*").permitAll()

                        // Razorpay webhooks
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()

                        // All authenticated user resources
                        .requestMatchers("/api/v1/profile/**", "/api/v1/users/me").authenticated()
                        .requestMatchers("/api/v1/history/**").authenticated()
                        .requestMatchers("/api/v1/billing/entitlement", "/api/v1/subscriptions/me").authenticated()
                        .requestMatchers("/api/v1/billing/orders", "/api/v1/subscriptions/checkout").authenticated()
                        .requestMatchers("/api/v1/billing/verify").authenticated()
                        .requestMatchers("/api/v1/payments/**").authenticated()

                        // Any other request
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler())
                )
                .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition", "Content-Length", "X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        return username -> {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Stateless Bearer token auth used");
        };
    }

    private AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> error = Map.of(
                    "error", Map.of(
                            "code", "LOGIN_REQUIRED",
                            "message", "Authentication required to access this resource"
                    )
            );
            objectMapper.writeValue(response.getOutputStream(), error);
        };
    }

    private AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> error = Map.of(
                    "error", Map.of(
                            "code", "FORBIDDEN",
                            "message", "You do not have permission to access this resource"
                    )
            );
            objectMapper.writeValue(response.getOutputStream(), error);
        };
    }
}
