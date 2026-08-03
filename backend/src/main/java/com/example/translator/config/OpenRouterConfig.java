package com.example.translator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class OpenRouterConfig {

    @Bean
    public RestClient openRouterRestClient(OpenRouterProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + (properties.getApiKey() == null ? "" : properties.getApiKey()))
                .defaultHeader("content-type", "application/json");

        // Optional, recommended by OpenRouter for attribution/rankings on openrouter.ai — not required for calls to work.
        if (properties.getSiteUrl() != null && !properties.getSiteUrl().isBlank()) {
            builder.defaultHeader("HTTP-Referer", properties.getSiteUrl());
        }
        if (properties.getAppName() != null && !properties.getAppName().isBlank()) {
            builder.defaultHeader("X-Title", properties.getAppName());
        }

        return builder.build();
    }
}
