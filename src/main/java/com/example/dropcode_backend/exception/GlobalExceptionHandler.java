package com.example.dropcode_backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        log.warn("API Exception [{}]: {}", ex.getCode(), ex.getMessage());
        Map<String, Object> errorMap = new HashMap<>(ex.getExtras());
        errorMap.put("code", ex.getCode());
        errorMap.put("message", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", errorMap);
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("code", "VALIDATION_FAILED");
        errorMap.put("message", "Request validation failed");
        errorMap.put("fields", errors);

        Map<String, Object> response = new HashMap<>();
        response.put("error", errorMap);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("code", "TRANSFER_TOO_LARGE");
        errorMap.put("message", "File upload exceeds maximum allowed payload limit");

        Map<String, Object> response = new HashMap<>();
        response.put("error", errorMap);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("code", "FORBIDDEN");
        errorMap.put("message", "Access denied: insufficient permissions");

        Map<String, Object> response = new HashMap<>();
        response.put("error", errorMap);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("code", "INTERNAL_SERVER_ERROR");
        errorMap.put("message", "An unexpected server error occurred");

        Map<String, Object> response = new HashMap<>();
        response.put("error", errorMap);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
