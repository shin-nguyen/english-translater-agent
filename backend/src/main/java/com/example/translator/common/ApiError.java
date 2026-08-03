package com.example.translator.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        int status,
        String message,
        Instant timestamp,
        String path,
        List<String> details
) {
    public static ApiError of(int status, String message, String path) {
        return new ApiError(status, message, Instant.now(), path, List.of());
    }

    public static ApiError of(int status, String message, String path, List<String> details) {
        return new ApiError(status, message, Instant.now(), path, details);
    }
}
