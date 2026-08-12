package com.example.translator.translation;

import com.example.translator.aimodel.AiModelConfig;
import com.example.translator.aimodel.AiProviderType;
import com.example.translator.common.AiServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicProviderClientTest {

    private final AnthropicProviderClient client = new AnthropicProviderClient();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AiModelConfig config(int port) {
        AiModelConfig config = new AiModelConfig();
        config.setLabel("Test Claude");
        config.setProvider(AiProviderType.ANTHROPIC);
        config.setBaseUrl("http://localhost:" + port);
        config.setApiKey("secret-key");
        config.setModelIdentifier("claude-sonnet-5");
        config.setMaxTokens(100);
        config.setTimeoutSeconds(5);
        return config;
    }

    @Test
    void callModel_postsToMessagesEndpointWithAuthHeadersAndReturnsText() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("x-api-key")).isEqualTo("secret-key");
            assertThat(exchange.getRequestHeaders().getFirst("anthropic-version")).isEqualTo("2023-06-01");
            String responseBody = "{\"id\":\"id1\",\"content\":[{\"type\":\"text\",\"text\":\"hello from claude\"}],\"stop_reason\":\"end_turn\"}";
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        String result = client.callModel(config(server.getAddress().getPort()), "system prompt", "user text");

        assertThat(result).isEqualTo("hello from claude");
    }

    @Test
    void callModel_throwsAiServiceExceptionOnEmptyContent() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            String responseBody = "{\"id\":\"id1\",\"content\":[],\"stop_reason\":\"end_turn\"}";
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        assertThatThrownBy(() -> client.callModel(config(server.getAddress().getPort()), "system", "user"))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void callModel_throwsAiServiceExceptionWhenServerUnreachable() throws IOException {
        HttpServer temp = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int port = temp.getAddress().getPort();
        temp.start();
        temp.stop(0);

        AiModelConfig unreachable = config(port);
        assertThatThrownBy(() -> client.callModel(unreachable, "system", "user"))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void supports_returnsAnthropic() {
        assertThat(client.supports()).isEqualTo(AiProviderType.ANTHROPIC);
    }
}
