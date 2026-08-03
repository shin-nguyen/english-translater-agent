package com.example.translator.note;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A rephrasing option with its style label (formal/casual/concise/...) and when to use it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NoteAlternativeItem(String text, String style, String reason) {
}
