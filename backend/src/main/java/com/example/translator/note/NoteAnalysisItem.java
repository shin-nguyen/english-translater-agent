package com.example.translator.note;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single concrete edit called out to the user: the exact snippet from their original input,
 * what it became in the result, and why. {@code ignoreUnknown} keeps deserialization of
 * previously-saved notes (which used an older {@code point}/{@code comment} shape) from blowing
 * up — those notes just show blank {@code improved}/{@code reason} fields for old entries.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NoteAnalysisItem(String original, String improved, String reason) {
}
