package com.bettingproject.collection.adapter.file;

import com.bettingproject.collection.adapter.replay.EvidenceReplayVerifier;
import com.bettingproject.collection.application.CollectionEndpoint;
import com.bettingproject.collection.application.EvidenceCollectionRequest;
import com.bettingproject.collection.application.ProviderCallResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFileEvidenceRepositoryTest {

    @TempDir
    java.nio.file.Path outputRoot;

    @Test
    void storesAnExactRawPayloadAndProducesAnOfflineReplayProof() throws Exception {
        Instant startedAt = Instant.parse("2026-08-11T11:30:00Z");
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T11:31:00Z"), ZoneOffset.UTC);
        ObjectMapper mapper = JsonMapper.builder().build();
        byte[] payload = "{\"data\":{\"id\":1347698848}}".getBytes(StandardCharsets.UTF_8);
        var request = new EvidenceCollectionRequest(
                "ENR-P01",
                "1347698848",
                CollectionEndpoint.DETAIL,
                "FIRST_AUTHORIZED",
                List.of("ID-01", "TIM-01", "OPS-01", "REP-01"));
        var response = new ProviderCallResult(
                "Highlightly",
                "/matches/1347698848",
                startedAt,
                startedAt.plusMillis(245),
                200,
                Map.of(
                        "content-type", "application/json",
                        "x-ratelimit-remaining", "79",
                        "authorization", "Bearer must-not-be-stored"),
                payload,
                "highlightly-enrichment-v1");

        var repository = new JsonFileEvidenceRepository(outputRoot, mapper, clock);
        var stored = repository.store(request, response);
        var firstReplay = new EvidenceReplayVerifier(mapper, clock).verify(stored);
        var secondReplay = new EvidenceReplayVerifier(mapper, clock).verify(stored);

        assertArrayEquals(payload, Files.readAllBytes(stored.rawPayload()));
        assertEquals("PASS", firstReplay.status());
        assertEquals("PASS", secondReplay.status());
        assertTrue(firstReplay.hashMatches());
        assertTrue(firstReplay.jsonValid());
        assertEquals(245, stored.latencyMs());

        JsonNode metadata = mapper.readTree(stored.metadata().toFile());
        assertEquals("/matches/1347698848", metadata.get("endpoint").asText());
        assertEquals("79", metadata.get("responseHeaders").get("x-ratelimit-remaining").asText());
        assertFalse(metadata.toString().toLowerCase().contains("x-rapidapi-key"));
        assertFalse(metadata.toString().toLowerCase().contains("authorization"));
    }
}
