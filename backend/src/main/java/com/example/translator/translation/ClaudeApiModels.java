package com.example.translator.translation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wire models for the Anthropic Messages API (POST /v1/messages).
 * Intentionally decoupled from {@link TranslationDtos}, our own API's response shape.
 */
public class ClaudeApiModels {

    private ClaudeApiModels() {
    }

    public record MessageRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<Message> messages
    ) {
    }

    public record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageResponse(
            String id,
            List<ContentBlock> content,
            @JsonProperty("stop_reason") String stopReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String text) {
    }
}
