package com.clothingplatform.exception;

import org.springframework.http.HttpStatus;

/** Thrown by services for any expected rule violation. Mapped to a clean API error. */
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
