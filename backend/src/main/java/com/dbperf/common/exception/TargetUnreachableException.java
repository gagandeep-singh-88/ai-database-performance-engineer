package com.dbperf.common.exception;

import org.springframework.http.HttpStatus;

/** The monitored target database could not be reached or queried. */
public class TargetUnreachableException extends ApiException {

    public TargetUnreachableException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}
