package com.bettingproject.collection.application.enrichment;

import java.time.Instant;
import java.util.Objects;

/** Bounded transport result; a null status means no response was reliably received. */
public record EnrichmentProviderResponse(Instant requestedAt, Instant completedAt,
        Integer httpStatus, byte[] body, Long quotaRemaining, String failureCode) {
    public EnrichmentProviderResponse {
        Objects.requireNonNull(requestedAt);
        Objects.requireNonNull(completedAt);
        body = Objects.requireNonNull(body).clone();
        if (completedAt.isBefore(requestedAt) || httpStatus != null && (httpStatus < 100 || httpStatus > 599)
                || quotaRemaining != null && quotaRemaining < 0
                || failureCode != null && !failureCode.matches("[A-Z][A-Z0-9_]{0,63}")
                || httpStatus == null && (failureCode == null || body.length != 0)) {
            throw new IllegalArgumentException("invalid provider response");
        }
    }
    @Override public byte[] body() { return body.clone(); }
}
