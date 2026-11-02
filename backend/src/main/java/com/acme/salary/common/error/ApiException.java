package com.acme.salary.common.error;

import org.springframework.http.HttpStatus;

/** Base for expected, client-facing failures. The {@code code} is a stable contract for the UI. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
