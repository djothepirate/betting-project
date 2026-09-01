package com.bettingproject.collection.adapter.file;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DailyProviderCallGuardTest {

    private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH-mm-ss-SSS'Z'")
            .withZone(ZoneOffset.UTC);

    @TempDir
    java.nio.file.Path outputRoot;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void preservesTheConfiguredProviderQuotaReserve() throws Exception {
        writeMetadata("""
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-11T10:00:00Z",
                  "httpStatus": 200,
                  "responseHeaders": {"x-ratelimit-requests-remaining": "20"}
                }
                """);
        var guard = guard(80, 20);

        assertThrows(IllegalStateException.class, () -> guard.assertAllowed("Highlightly"));
    }

    @Test
    void suspendsCallsUntilDiagnosisAfterAuthenticationFailure() throws Exception {
        writeMetadata("""
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-11T10:00:00Z",
                  "httpStatus": 401,
                  "responseHeaders": {}
                }
                """);

        assertThrows(IllegalStateException.class, () -> guard(80, 20).assertAllowed("Highlightly"));
    }

    @Test
    void doesNotForgetAnAuthenticationFailureAtParisMidnight() throws Exception {
        writeMetadata("auth-failure.metadata.json", """
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-10T21:59:59Z",
                  "httpStatus": 403,
                  "responseHeaders": {}
                }
                """);

        assertThrows(IllegalStateException.class, () -> guard(80, 20).assertAllowed("Highlightly"));
    }

    @Test
    void subtractsLaterCallsWithoutQuotaHeadersFromTheLastObservedCounter() throws Exception {
        writeMetadata("quota.metadata.json", """
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-11T10:00:00Z",
                  "httpStatus": 200,
                  "responseHeaders": {"x-ratelimit-requests-remaining": "21"}
                }
                """);
        writeMetadata("without-quota.metadata.json", """
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-11T10:01:00Z",
                  "httpStatus": 200,
                  "responseHeaders": {}
                }
                """);

        assertThrows(IllegalStateException.class, () -> guard(80, 20).assertAllowed("Highlightly"));
    }

    @Test
    void rejectsANonNumericProviderQuotaHeader() throws Exception {
        writeMetadata("""
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-11T10:00:00Z",
                  "httpStatus": 200,
                  "responseHeaders": {"x-ratelimit-requests-remaining": "unknown"}
                }
                """);

        assertThrows(java.io.IOException.class, () -> guard(80, 20).assertAllowed("Highlightly"));
    }

    @Test
    void rejectsANegativeProviderQuotaHeader() throws Exception {
        writeMetadata("""
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-11T10:00:00Z",
                  "httpStatus": 200,
                  "responseHeaders": {"x-ratelimit-requests-remaining": "-1"}
                }
                """);

        assertThrows(java.io.IOException.class, () -> guard(80, 20).assertAllowed("Highlightly"));
    }

    @Test
    void usesTheMostConservativeOfMultipleProviderQuotaHeaders() throws Exception {
        writeMetadata("""
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-11T10:00:00Z",
                  "httpStatus": 200,
                  "responseHeaders": {
                    "x-ratelimit-requests-remaining": "21",
                    "x-requestcounter-remaining": "19"
                  }
                }
                """);

        assertThrows(IllegalStateException.class, () -> guard(80, 20).assertAllowed("Highlightly"));
    }

    @Test
    void rejectsAnIsoTimestampThatDoesNotMatchTheRunId() throws Exception {
        writeMetadataAtRun("2026-08-11T10-00-00-000Z-a1b2c3d4", "detail.metadata.json", """
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-11T11:00:00Z",
                  "httpStatus": 200,
                  "responseHeaders": {"x-ratelimit-requests-remaining": "79"}
                }
                """);

        assertThrows(java.io.IOException.class, () -> guard(80, 20).assertAllowed("Highlightly"));
    }

    @Test
    void rejectsAnArbitraryNonIsoTimestampInsteadOfTreatingItAsLegacy() throws Exception {
        writeMetadataAtRun("2026-08-11T10-00-00-000Z-a1b2c3d4", "detail.metadata.json", """
                {
                  "provider": "Highlightly",
                  "startedAt": "not-a-timestamp",
                  "httpStatus": 200,
                  "responseHeaders": {"x-ratelimit-requests-remaining": "79"}
                }
                """);

        assertThrows(java.io.IOException.class, () -> guard(80, 20).assertAllowed("Highlightly"));
    }

    @Test
    void countsOnlyCallsFromTheCurrentBudgetDay() throws Exception {
        writeMetadata("""
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-10T10:00:00Z",
                  "httpStatus": 200,
                  "responseHeaders": {"x-ratelimit-requests-remaining": "80"}
                }
                """);
        var status = guard(80, 20).assertAllowed("Highlightly");

        assertEquals(0, status.recordedCalls());
        assertEquals(80, status.effectiveProviderRemaining());
    }

    @Test
    void doesNotAssumeThatProviderQuotaResetsAtParisMidnight() throws Exception {
        writeMetadata("""
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-10T22:30:00Z",
                  "httpStatus": 200,
                  "responseHeaders": {"x-ratelimit-requests-remaining": "20"}
                }
                """);

        assertThrows(IllegalStateException.class, () -> guard(80, 20).assertAllowed("Highlightly"));
    }

    @Test
    void fallsBackToTheUtcRunIdForLegacyLocalTimestamps() throws Exception {
        java.nio.file.Path directory = Files.createDirectories(outputRoot
                .resolve("2026-08-11T10-00-00-000Z-a1b2c3d4")
                .resolve("ENR-P01")
                .resolve("highlightly")
                .resolve("t-60"));
        Files.writeString(directory.resolve("detail.metadata.json"), """
                {
                  "provider": "Highlightly",
                  "startedAt": "11/08/2026 12:00:00",
                  "httpStatus": 200,
                  "responseHeaders": {"x-ratelimit-requests-remaining": "79"}
                }
                """);

        var status = guard(80, 20).assertAllowed("Highlightly");

        assertEquals(1, status.recordedCalls());
        assertEquals(79, status.effectiveProviderRemaining());
    }

    @Test
    void enforcesTheLocalOperationalMaximum() throws Exception {
        writeMetadata("""
                {
                  "provider": "Highlightly",
                  "startedAt": "2026-08-11T10:00:00Z",
                  "httpStatus": 200,
                  "responseHeaders": {}
                }
                """);

        assertThrows(IllegalStateException.class, () -> guard(1, 0).assertAllowed("Highlightly"));
    }

    private DailyProviderCallGuard guard(int maximum, int reserve) {
        return new DailyProviderCallGuard(
                outputRoot,
                JsonMapper.builder().build(),
                clock,
                ZoneId.of("Europe/Paris"),
                maximum,
                reserve);
    }

    private void writeMetadata(String json) throws Exception {
        writeMetadata("detail.metadata.json", json);
    }

    private void writeMetadata(String fileName, String json) throws Exception {
        String startedAt = JsonMapper.builder().build().readTree(json).path("startedAt").asText();
        String runId = RUN_ID_FORMAT.format(Instant.parse(startedAt)) + "-a1b2c3d4";
        writeMetadataAtRun(runId, fileName, json);
    }

    private void writeMetadataAtRun(String runId, String fileName, String json) throws Exception {
        java.nio.file.Path directory = Files.createDirectories(outputRoot
                .resolve(runId)
                .resolve("ENR-P01")
                .resolve("highlightly")
                .resolve("test"));
        Files.writeString(directory.resolve(fileName), json);
    }
}
