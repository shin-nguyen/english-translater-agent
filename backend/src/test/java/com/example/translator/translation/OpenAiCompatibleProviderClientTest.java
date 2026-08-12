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

class OpenAiCompatibleProviderClientTest {

    private final OpenAiCompatibleProviderClient client = new OpenAiCompatibleProviderClient();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AiModelConfig config(int port, String apiKey) {
        AiModelConfig config = new AiModelConfig();
        config.setLabel("Test OpenAI-compatible");
        config.setProvider(AiProviderType.OPENAI_COMPATIBLE);
        config.setBaseUrl("http://localhost:" + port);
        config.setApiKey(apiKey);
        config.setModelIdentifier("some-model");
        config.setMaxTokens(100);
        config.setTimeoutSeconds(5);
        return config;
    }

    private void respondWith(String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @Test
    void callModel_sendsAuthorizationHeaderWhenApiKeySet() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer secret-key");
            String responseBody = "{\"id\":\"id1\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}";
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        String result = client.callModel(config(server.getAddress().getPort(), "secret-key"), "system", "user");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void callModel_omitsAuthorizationHeaderWhenApiKeyBlank() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
            String responseBody = "{\"id\":\"id1\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}";
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        String result = client.callModel(config(server.getAddress().getPort(), ""), "system", "user");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void callModel_throwsAiServiceExceptionOnEmptyChoices() throws IOException {
        respondWith("{\"id\":\"id1\",\"choices\":[]}");

        assertThatThrownBy(() -> client.callModel(config(server.getAddress().getPort(), "key"), "system", "user"))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void supports_returnsOpenAiCompatible() {
        assertThat(client.supports()).isEqualTo(AiProviderType.OPENAI_COMPATIBLE);
    }
}
