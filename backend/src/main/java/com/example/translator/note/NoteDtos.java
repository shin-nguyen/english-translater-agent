package com.example.translator.note;

import com.example.translator.context.ContextDtos.ContextResponse;
import com.example.translator.role.RoleDtos.RoleResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class NoteDtos {

    private static final int EXCERPT_LENGTH = 160;

    private NoteDtos() {
    }

    public record NoteCreateRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 2000) String originalText,
            @NotBlank @Size(max = 10) String detectedLanguage,
            @NotBlank String englishResult,
            List<NoteAlternativeItem> alternatives,
            List<NoteAnalysisItem> analysis,
            Long roleId,
            Long contextId
    ) {
    }

    public record NoteUpdateRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 2000) String originalText,
            @NotBlank @Size(max = 10) String detectedLanguage,
            @NotBlank String englishResult,
            List<NoteAlternativeItem> alternatives,
            List<NoteAnalysisItem> analysis,
            Long roleId,
            Long contextId
    ) {
    }

    public record NoteResponse(
            Long id,
            String title,
            String originalText,
            String detectedLanguage,
            String englishResult,
            List<NoteAlternativeItem> alternatives,
            List<NoteAnalysisItem> analysis,
            RoleResponse role,
            ContextResponse context,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static NoteResponse from(Note note) {
            return new NoteResponse(
                    note.getId(),
                    note.getTitle(),
                    note.getOriginalText(),
                    note.getDetectedLanguage(),
                    note.getEnglishResult(),
                    note.getAlternatives(),
                    note.getAnalysis(),
                    note.getRole() != null ? RoleResponse.from(note.getRole()) : null,
                    note.getContext() != null ? ContextResponse.from(note.getContext()) : null,
                    note.getCreatedAt(),
                    note.getUpdatedAt()
            );
        }
    }

    public record NoteSummaryResponse(
            Long id,
            String title,
            String originalTextExcerpt,
            String englishResultExcerpt,
            RoleResponse role,
            ContextResponse context,
            Instant createdAt
    ) {
        public static NoteSummaryResponse from(Note note) {
            return new NoteSummaryResponse(
                    note.getId(),
                    note.getTitle(),
                    excerpt(note.getOriginalText()),
                    excerpt(note.getEnglishResult()),
                    note.getRole() != null ? RoleResponse.from(note.getRole()) : null,
                    note.getContext() != null ? ContextResponse.from(note.getContext()) : null,
                    note.getCreatedAt()
            );
        }

        private static String excerpt(String text) {
            if (text == null) {
                return "";
            }
            return text.length() > EXCERPT_LENGTH ? text.substring(0, EXCERPT_LENGTH) + "..." : text;
        }
    }
}
