package com.clothingplatform.exception;

import org.springframework.http.HttpStatus;

/** Base for all expected business errors. Carries a stable machine-readable code for the frontend. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
