package com.example.translator.aimodel;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class AiModelConfigDtos {

    private AiModelConfigDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 100) String label,
            @NotNull AiProviderType provider,
            @NotBlank @Size(max = 500) String baseUrl,
            @NotBlank String apiKey,
            @NotBlank @Size(max = 200) String modelIdentifier,
            @Size(max = 30) String apiVersion,
            @Min(1) int maxTokens,
            @Min(1) int timeoutSeconds,
            boolean enabled,
            boolean isDefault
    ) {
    }

    // apiKey blank/null on update means "leave the stored key unchanged"
    public record UpdateRequest(
            @NotBlank @Size(max = 100) String label,
            @NotNull AiProviderType provider,
            @NotBlank @Size(max = 500) String baseUrl,
            String apiKey,
            @NotBlank @Size(max = 200) String modelIdentifier,
            @Size(max = 30) String apiVersion,
            @Min(1) int maxTokens,
            @Min(1) int timeoutSeconds,
            boolean enabled,
            boolean isDefault
    ) {
    }

    // never includes apiKey, even to admins — write-only through Create/Update
    public record AdminResponse(
            Long id, String label, AiProviderType provider, String baseUrl, String modelIdentifier,
            String apiVersion, int maxTokens, int timeoutSeconds, boolean enabled, boolean isDefault,
            Instant createdAt, Instant updatedAt
    ) {
        public static AdminResponse from(AiModelConfig c) {
            return new AdminResponse(c.getId(), c.getLabel(), c.getProvider(), c.getBaseUrl(), c.getModelIdentifier(),
                    c.getApiVersion(), c.getMaxTokens(), c.getTimeoutSeconds(), c.isEnabled(), c.isDefault(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    // minimal shape for the basic-user model picker — id + label only
    public record OptionResponse(Long id, String label) {
        public static OptionResponse from(AiModelConfig c) {
            return new OptionResponse(c.getId(), c.getLabel());
        }
    }
}
