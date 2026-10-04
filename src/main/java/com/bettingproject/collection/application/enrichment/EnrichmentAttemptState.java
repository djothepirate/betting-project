package com.bettingproject.collection.application.enrichment;

public enum EnrichmentAttemptState {
    RESERVED,
    COMMITTED_FOR_SEND,
    RECEIVED,
    HTTP_ERROR,
    UNCERTAIN,
    RESPONSE_TOO_LARGE,
    SECRET_ECHO,
    RESPONSE_INTERRUPTED,
    RELEASED
}
