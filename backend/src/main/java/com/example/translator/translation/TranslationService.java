package com.example.translator.translation;

import com.example.translator.translation.TranslationDtos.TranslateRequest;
import com.example.translator.translation.TranslationDtos.TranslateResponse;

/**
 * Provider-agnostic translation/style-polish service. {@link TranslationServiceImpl} holds the
 * shared prompt/parsing/retry logic and dispatches each request to the right
 * {@link AiProviderClient} (resolved via {@link AiProviderClientRegistry}) based on the
 * admin-configured {@link com.example.translator.aimodel.AiModelConfig} the caller selected.
 */
public interface TranslationService {

    TranslateResponse translate(TranslateRequest request);
}
