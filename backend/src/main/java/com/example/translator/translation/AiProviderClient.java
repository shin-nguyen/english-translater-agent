package com.example.translator.translation;

import com.example.translator.aimodel.AiModelConfig;
import com.example.translator.aimodel.AiProviderType;

/**
 * Knows how to make one AI provider's HTTP call. Implementations are stateless and build a
 * fresh {@link org.springframework.web.client.RestClient} per call from the given
 * {@link AiModelConfig}, so an admin editing a config's baseUrl/key takes effect immediately —
 * no stale-client caching to worry about.
 */
public interface AiProviderClient {

    AiProviderType supports();

    String callModel(AiModelConfig config, String systemPrompt, String userText);
}
