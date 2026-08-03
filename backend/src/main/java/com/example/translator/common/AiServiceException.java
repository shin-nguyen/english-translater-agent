package com.example.translator.common;

/**
 * Thrown when the upstream AI provider (Claude) fails: network/timeout error,
 * non-2xx response, or a response that cannot be parsed into the expected JSON schema
 * even after a retry.
 */
public class AiServiceException extends RuntimeException {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
