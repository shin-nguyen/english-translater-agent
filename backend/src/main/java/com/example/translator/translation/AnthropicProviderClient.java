package com.example.translator.translation;

import com.example.translator.aimodel.AiModelConfig;
import com.example.translator.aimodel.AiProviderType;
import com.example.translator.common.AiServiceException;
import com.example.translator.translation.ClaudeApiModels.Message;
import com.example.translator.translation.ClaudeApiModels.MessageRequest;
import com.example.translator.translation.ClaudeApiModels.MessageResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Talks to the Anthropic Messages API (POST /v1/messages) — a genuinely different wire
 * protocol from OpenAI-compatible chat-completions: {@code x-api-key}/{@code anthropic-version}
 * headers, a top-level {@code system} field, and a {@code content[0].text} response shape.
 */
@Component
public class AnthropicProviderClient implements AiProviderClient {

    private static final String DEFAULT_API_VERSION = "2023-06-01";

    @Override
    public AiProviderType supports() {
        return AiProviderType.ANTHROPIC;
    }

    @Override
    public String callModel(AiModelConfig config, String systemPrompt, String userText) {
        RestClient restClient = buildRestClient(config);

        MessageRequest body = new MessageRequest(
                config.getModelIdentifier(),
                config.getMaxTokens(),
                systemPrompt,
                List.of(new Message("user", userText))
        );

        MessageResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/messages")
                    .body(body)
                    .retrieve()
                    .body(MessageResponse.class);
        } catch (RestClientException e) {
            throw new AiServiceException("Không thể kết nối tới dịch vụ AI: " + e.getMessage(), e);
        }

        if (response == null || response.content() == null || response.content().isEmpty()
                || response.content().get(0).text() == null) {
            throw new AiServiceException("Dịch vụ AI trả về phản hồi rỗng.");
        }
        return response.content().get(0).text();
    }

    private RestClient buildRestClient(AiModelConfig config) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(config.getTimeoutSeconds()));

        String apiVersion = config.getApiVersion() == null || config.getApiVersion().isBlank()
                ? DEFAULT_API_VERSION
                : config.getApiVersion();

        return RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("x-api-key", config.getApiKey() == null ? "" : config.getApiKey())
                .defaultHeader("anthropic-version", apiVersion)
                .defaultHeader("content-type", "application/json")
                .build();
    }
}
