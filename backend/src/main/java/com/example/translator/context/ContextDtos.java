package com.example.translator.context;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class ContextDtos {

    private ContextDtos() {
    }

    public record ContextResponse(Long id, String name, String description, Instant createdAt) {
        public static ContextResponse from(Context context) {
            return new ContextResponse(context.getId(), context.getName(), context.getDescription(), context.getCreatedAt());
        }
    }

    public record ContextRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String description
    ) {
    }
}
