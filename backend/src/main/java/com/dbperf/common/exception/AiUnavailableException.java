package com.dbperf.common.exception;

import org.springframework.http.HttpStatus;

/** The AI backend is not configured or failed to produce a result. */
public class AiUnavailableException extends ApiException {

    public AiUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
