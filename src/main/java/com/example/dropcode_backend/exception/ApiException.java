package com.example.dropcode_backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

@Getter
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> extras;

    public ApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
        this.extras = Collections.emptyMap();
    }

    public ApiException(String code, String message, HttpStatus status, Map<String, Object> extras) {
        super(message);
        this.code = code;
        this.status = status;
        this.extras = extras != null ? extras : Collections.emptyMap();
    }
}
