package com.clothingplatform.common;

import java.time.Instant;

/** Uniform envelope for every API response. */
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, data, null, Instant.now()); }
    public static <T> ApiResponse<T> failure(ApiError error) { return new ApiResponse<>(false, null, error, Instant.now()); }
}
