package com.bettingproject.catalog.adapter.web;

import org.springframework.http.HttpStatus;

final class CatalogWebException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    CatalogWebException(HttpStatus status, String code) {
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

    static CatalogWebException badRequest(String code) {
        return new CatalogWebException(HttpStatus.BAD_REQUEST, code);
    }

    static CatalogWebException notFound(String code) {
        return new CatalogWebException(HttpStatus.NOT_FOUND, code);
    }

    static CatalogWebException conflict(String code) {
        return new CatalogWebException(HttpStatus.CONFLICT, code);
    }

    static CatalogWebException unprocessable(String code) {
        return new CatalogWebException(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }

    static CatalogWebException unavailable(String code) {
        return new CatalogWebException(HttpStatus.SERVICE_UNAVAILABLE, code);
    }
}
