package com.example.translator.translation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wire models for OpenRouter's OpenAI-compatible Chat Completions API (POST /chat/completions).
 * Intentionally decoupled from {@link TranslationDtos}, our own API's response shape.
 */
public class OpenRouterApiModels {

    private OpenRouterApiModels() {
    }

    public record ChatCompletionRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            List<ChatMessage> messages
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatMessage(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionResponse(
            String id,
            List<Choice> choices
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(ChatMessage message) {
    }
}
