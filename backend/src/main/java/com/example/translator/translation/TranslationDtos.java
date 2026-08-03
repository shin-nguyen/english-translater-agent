package com.example.translator.translation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class TranslationDtos {

    private TranslationDtos() {
    }

    public record TranslateRequest(
            @NotBlank(message = "text must not be blank")
            @Size(max = 2000, message = "text must be at most 2000 characters")
            String text,
            Long roleId,
            Long contextId
    ) {
    }

    public record TranslateResponse(
            String detectedLanguage,
            String suggestedTitle,
            String mainResult,
            List<Alternative> alternatives,
            List<AnalysisPoint> analysis
    ) {
    }

    public record AnalysisPoint(String original, String improved, String reason) {
    }

    public record Alternative(String text, String style, String reason) {
    }
}
