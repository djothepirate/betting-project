package com.bettingproject.collection.adapter.web.j7;

import org.springframework.http.HttpStatus;

final class J7ImportWebException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private J7ImportWebException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }

    static J7ImportWebException conflict() {
        return new J7ImportWebException(HttpStatus.CONFLICT, "J7_IMPORT_CONFLICT");
    }

    static J7ImportWebException forbidden() {
        return new J7ImportWebException(HttpStatus.FORBIDDEN, "J7_CLIENT_IDENTITY_REQUIRED");
    }
}
