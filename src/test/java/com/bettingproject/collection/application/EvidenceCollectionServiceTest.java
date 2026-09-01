package com.bettingproject.collection.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceCollectionServiceTest {

    @Test
    void performsExactlyOneProviderCallAndStoresItsResult() throws Exception {
        AtomicInteger clientCalls = new AtomicInteger();
        AtomicInteger storeCalls = new AtomicInteger();
        var request = new EvidenceCollectionRequest(
                "ENR-P01",
                "1347698848",
                CollectionEndpoint.DETAIL,
                "FIRST_AUTHORIZED",
                List.of("ID-01"));
        ProviderCallResult response = new ProviderCallResult(
                "Highlightly",
                "/matches/1347698848",
                Instant.parse("2026-08-11T11:30:00Z"),
                Instant.parse("2026-08-11T11:30:00.100Z"),
                200,
                Map.of(),
                "{}".getBytes(StandardCharsets.UTF_8),
                "test-v1");

        ProviderMatchClient client = new ProviderMatchClient() {
            @Override
            public String provider() {
                return "Highlightly";
            }

            @Override
            public ProviderCallResult fetch(EvidenceCollectionRequest ignored) {
                clientCalls.incrementAndGet();
                return response;
            }
        };
        EvidenceRepository repository = (ignoredRequest, ignoredResponse) -> {
            storeCalls.incrementAndGet();
            return new StoredEvidence("call-id", java.nio.file.Path.of("raw.json"),
                    java.nio.file.Path.of("metadata.json"), "hash", 200, 100);
        };

        StoredEvidence stored = new EvidenceCollectionService(client, repository).collect(request);

        assertEquals("call-id", stored.callId());
        assertEquals(1, clientCalls.get());
        assertEquals(1, storeCalls.get());
    }
}
