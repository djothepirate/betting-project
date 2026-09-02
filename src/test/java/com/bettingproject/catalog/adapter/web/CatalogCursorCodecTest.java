package com.bettingproject.catalog.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.bettingproject.catalog.application.AttemptKeysetAnchor;
import com.bettingproject.catalog.application.CorrelationKeysetAnchor;
import com.bettingproject.catalog.application.TimestampKeysetAnchor;
import org.junit.jupiter.api.Test;

class CatalogCursorCodecTest {

    private static final String FILTER = "a".repeat(64);
    private final CatalogCursorCodec codec = new CatalogCursorCodec();

    @Test
    void roundTripsEverySupportedAnchorWithoutPadding() {
        TimestampKeysetAnchor timestamp = new TimestampKeysetAnchor(
                Instant.parse("2026-09-02T00:00:00Z"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        AttemptKeysetAnchor attempt = new AttemptKeysetAnchor(
                7,
                UUID.fromString("00000000-0000-0000-0000-000000000002"));
        CorrelationKeysetAnchor correlation = new CorrelationKeysetAnchor(
                Instant.parse("2026-09-02T01:00:00Z"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"));

        String timestampCursor = codec.encodeTimestamp("anomalies", FILTER, timestamp);
        String attemptCursor = codec.encodeAttempt("attempts", FILTER, attempt);
        String correlationCursor = codec.encodeCorrelation("correlations", FILTER, correlation);

        assertThat(timestampCursor).doesNotContain("=");
        assertThat(attemptCursor).doesNotContain("=");
        assertThat(correlationCursor).doesNotContain("=");
        assertThat(codec.decodeTimestamp(timestampCursor, "anomalies", FILTER))
                .isEqualTo(timestamp);
        assertThat(codec.decodeAttempt(attemptCursor, "attempts", FILTER))
                .isEqualTo(attempt);
        assertThat(codec.decodeCorrelation(correlationCursor, "correlations", FILTER))
                .isEqualTo(correlation);
    }

    @Test
    void rejectsCursorBoundToAnotherScopeOrFilter() {
        String cursor = codec.encodeTimestamp(
                "anomalies",
                FILTER,
                new TimestampKeysetAnchor(Instant.EPOCH, UUID.randomUUID()));

        assertInvalidCursor(() -> codec.decodeTimestamp(cursor, "provider-mappings", FILTER));
        assertInvalidCursor(() -> codec.decodeTimestamp(cursor, "anomalies", "b".repeat(64)));
    }

    @Test
    void rejectsMalformedPaddedTruncatedAndWrongAnchorCursors() {
        String cursor = codec.encodeAttempt(
                "attempts",
                FILTER,
                new AttemptKeysetAnchor(1, UUID.randomUUID()));

        assertInvalidCursor(() -> codec.decodeAttempt("%%%", "attempts", FILTER));
        assertInvalidCursor(() -> codec.decodeAttempt(cursor + "=", "attempts", FILTER));
        assertInvalidCursor(() -> codec.decodeAttempt(cursor.substring(0, 5), "attempts", FILTER));
        assertInvalidCursor(() -> codec.decodeTimestamp(cursor, "attempts", FILTER));
    }

    private void assertInvalidCursor(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(CatalogWebException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(400);
                    assertThat(exception.code()).isEqualTo("INVALID_CURSOR");
                });
    }
}
