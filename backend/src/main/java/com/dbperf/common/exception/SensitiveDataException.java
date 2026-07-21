package com.dbperf.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a payload still contains detectable sensitive data after
 * sanitization, or when AI is disabled by policy. The request is blocked
 * before anything is sent to the AI provider. The message describes the
 * categories detected — never the raw values.
 */
public class SensitiveDataException extends ApiException {

    public SensitiveDataException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
