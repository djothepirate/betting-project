package com.bettingproject.collection.application.calendar;

import java.time.Instant;
import java.util.Objects;

public record CalendarPageResponse(
        Instant requestedAt, Instant receivedAt, int httpStatus, byte[] body,
        Long quotaRemaining, String failureCode) {
    public CalendarPageResponse {
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        body = Objects.requireNonNull(body, "body").clone();
        if (receivedAt.isBefore(requestedAt) || (httpStatus != 0 && (httpStatus < 100 || httpStatus > 599))
                || (quotaRemaining != null && quotaRemaining < 0)
                || (failureCode != null && !failureCode.matches("[A-Z][A-Z_]{0,63}"))
                || (httpStatus == 0 && failureCode == null)) {
            throw new IllegalArgumentException("Invalid calendar page response");
        }
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
