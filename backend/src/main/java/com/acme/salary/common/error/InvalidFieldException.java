package com.acme.salary.common.error;

import org.springframework.http.HttpStatus;

/** A business-rule validation failure tied to one request field (rendered like bean-validation errors). */
public class InvalidFieldException extends ApiException {

    private final String field;

    public InvalidFieldException(String field, String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
