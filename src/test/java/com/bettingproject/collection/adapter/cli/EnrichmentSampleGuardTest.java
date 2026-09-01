package com.bettingproject.collection.adapter.cli;

import com.bettingproject.collection.application.CollectionEndpoint;
import com.bettingproject.collection.application.EvidenceCollectionRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnrichmentSampleGuardTest {

    private final EnrichmentSampleGuard guard = new EnrichmentSampleGuard(JsonMapper.builder().build());
    private final Path manifest = Path.of("docs", "benchmark", "enrichment-sample-v0.1.json");

    @Test
    void acceptsTheApprovedPsgAstonVillaBaseline() {
        var request = new EvidenceCollectionRequest(
                "ENR-P01",
                "1347698848",
                CollectionEndpoint.DETAIL,
                "FIRST_AUTHORIZED",
                List.of("ID-01", "TIM-01", "OPS-01", "REP-01"));

        assertDoesNotThrow(() -> guard.validate(manifest, request));
    }

    @Test
    void rejectsAMatchIdThatDiffersFromTheManifest() {
        var request = new EvidenceCollectionRequest(
                "ENR-P01",
                "999999",
                CollectionEndpoint.DETAIL,
                "FIRST_AUTHORIZED",
                List.of("ID-01"));

        assertThrows(IllegalArgumentException.class, () -> guard.validate(manifest, request));
    }

    @Test
    void preventsFullEnrichmentOfAnUnactivatedReserve() {
        var request = new EvidenceCollectionRequest(
                "ENR-R01",
                "1368042854",
                CollectionEndpoint.LINEUP,
                "T-60",
                List.of("LIN-01", "OPS-01", "REP-01"));

        assertThrows(IllegalArgumentException.class, () -> guard.validate(manifest, request));
    }

    @Test
    void rejectsScenariosThatDoNotBelongToTheEndpointFamily() {
        var request = new EvidenceCollectionRequest(
                "ENR-P01",
                "1347698848",
                CollectionEndpoint.DETAIL,
                "FIRST_AUTHORIZED",
                List.of("LIN-01"));

        assertThrows(IllegalArgumentException.class, () -> guard.validate(manifest, request));
    }
}
