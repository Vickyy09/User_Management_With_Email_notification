package com.vicky.userManagment.exception;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String message, String path,
                       Map<String, String> fieldErrors) {
    public static ApiError of(int status, String message, String path) {
        return new ApiError(Instant.now(), status, message, path, Map.of());
    }
}