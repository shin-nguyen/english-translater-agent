package com.example.translator.translation;

import com.example.translator.aimodel.AiModelConfig;
import com.example.translator.aimodel.AiProviderType;
import com.example.translator.common.AiServiceException;
import com.example.translator.translation.OpenAiApiModels.ChatCompletionRequest;
import com.example.translator.translation.OpenAiApiModels.ChatCompletionResponse;
import com.example.translator.translation.OpenAiApiModels.ChatMessage;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Talks to any OpenAI-compatible chat-completions endpoint (POST {baseUrl}/chat/completions) —
 * OpenRouter, a self-hosted vLLM/llama.cpp/Ollama server, etc. Sends {@code Authorization: Bearer}
 * only when an API key is configured, since self-hosted servers often don't require one.
 */
@Component
public class OpenAiCompatibleProviderClient implements AiProviderClient {

    @Override
    public AiProviderType supports() {
        return AiProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public String callModel(AiModelConfig config, String systemPrompt, String userText) {
        RestClient restClient = buildRestClient(config);

        ChatCompletionRequest body = new ChatCompletionRequest(
                config.getModelIdentifier(),
                config.getMaxTokens(),
                List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", userText))
        );

        ChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientException e) {
            throw new AiServiceException("Không thể kết nối tới dịch vụ AI: " + e.getMessage(), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null) {
            throw new AiServiceException("Dịch vụ AI trả về phản hồi rỗng.");
        }
        return response.choices().get(0).message().content();
    }

    private RestClient buildRestClient(AiModelConfig config) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("content-type", "application/json");

        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + config.getApiKey());
        }

        return builder.build();
    }
}
