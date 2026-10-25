package com.example.dropcode_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Health Checks", description = "Liveness and readiness probes")
public class HealthCheckController {

    private final DataSource dataSource;

    @GetMapping("/healthz")
    @Operation(summary = "Liveness probe")
    public ResponseEntity<Map<String, String>> healthz() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/readyz")
    @Operation(summary = "Readiness probe: validates database connectivity")
    public ResponseEntity<Map<String, Object>> readyz() {
        boolean dbOk = false;
        try (Connection conn = dataSource.getConnection()) {
            dbOk = conn.isValid(2);
        } catch (Exception ignored) {}

        if (dbOk) {
            return ResponseEntity.ok(Map.of("status", "READY", "database", "CONNECTED"));
        } else {
            return ResponseEntity.status(503).body(Map.of("status", "NOT_READY", "database", "DISCONNECTED"));
        }
    }
}
