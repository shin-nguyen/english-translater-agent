package com.example.translator.translation;

import com.example.translator.translation.TranslationDtos.TranslateRequest;
import com.example.translator.translation.TranslationDtos.TranslateResponse;

/**
 * Provider-agnostic translation/style-polish service. The Claude-backed implementation
 * lives in {@link ClaudeTranslationServiceImpl}; swap in another provider by implementing
 * this interface and re-pointing the Spring bean.
 */
public interface TranslationService {

    TranslateResponse translate(TranslateRequest request);
}
