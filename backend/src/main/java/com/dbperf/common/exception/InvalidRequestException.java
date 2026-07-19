package com.dbperf.common.exception;

import org.springframework.http.HttpStatus;

/** Semantically invalid request that bean validation alone can't express. */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
