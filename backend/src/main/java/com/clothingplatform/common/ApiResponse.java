package com.clothingplatform.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Uniform envelope for every API response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

    public record ApiError(String code, String message, Object details) {}

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static ApiResponse<Void> fail(String code, String message, Object details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), Instant.now());
    }
}
