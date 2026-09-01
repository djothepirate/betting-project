package com.bettingproject.collection.adapter.cli;

import com.bettingproject.collection.application.CollectionEndpoint;
import com.bettingproject.collection.application.EvidenceCollectionRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class EnrichmentSampleGuard {

    private static final Map<CollectionEndpoint, Set<String>> ENDPOINT_SCENARIOS = Map.of(
            CollectionEndpoint.DETAIL, Set.of("ID-01", "TIM-01", "OPS-01", "REP-01"),
            CollectionEndpoint.LINEUP, Set.of("LIN-01", "OPS-01", "REP-01"),
            CollectionEndpoint.STATISTICS, Set.of("STA-01", "OPS-01", "REP-01"),
            CollectionEndpoint.EVENTS, Set.of("EVT-01", "OPS-01", "REP-01"),
            CollectionEndpoint.BOX_SCORE, Set.of("PLY-01", "OPS-01", "REP-01"));
    private static final Map<CollectionEndpoint, Set<String>> REQUIRED_SCENARIOS = Map.of(
            CollectionEndpoint.DETAIL, Set.of("TIM-01", "OPS-01", "REP-01"),
            CollectionEndpoint.LINEUP, Set.of("LIN-01", "OPS-01", "REP-01"),
            CollectionEndpoint.STATISTICS, Set.of("STA-01", "OPS-01", "REP-01"),
            CollectionEndpoint.EVENTS, Set.of("EVT-01", "OPS-01", "REP-01"),
            CollectionEndpoint.BOX_SCORE, Set.of("PLY-01", "OPS-01", "REP-01"));

    private final ObjectMapper objectMapper;

    EnrichmentSampleGuard(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void validate(Path manifestPath, EvidenceCollectionRequest request) throws IOException {
        JsonNode manifest = objectMapper.readTree(manifestPath.toFile());
        if (!"enrichment-sample-v1".equals(text(manifest, "schemaVersion"))
                || !"ACCEPTED".equals(text(manifest, "status"))) {
            throw new IllegalArgumentException("The enrichment manifest is not an accepted v1 manifest");
        }

        ManifestMatch match = findMatch(manifest, request.sampleId());
        if (match == null) {
            throw new IllegalArgumentException("sampleId is absent from the accepted enrichment manifest");
        }
        if (!match.highlightlyMatchId().equals(request.providerMatchId())) {
            throw new IllegalArgumentException("matchId does not match the accepted manifest entry");
        }

        Set<String> allowedScenarios = ENDPOINT_SCENARIOS.get(request.endpoint());
        if (!allowedScenarios.containsAll(request.scenarios())) {
            throw new IllegalArgumentException("scenarios are incompatible with the selected endpoint family");
        }
        if (!request.scenarios().containsAll(REQUIRED_SCENARIOS.get(request.endpoint()))) {
            throw new IllegalArgumentException("required scenarios are missing for the selected endpoint family");
        }
        if (match.kind() != MatchKind.PRIMARY && request.endpoint() != CollectionEndpoint.DETAIL) {
            throw new IllegalArgumentException("reserve and control matches cannot receive full enrichment before activation");
        }
    }

    private ManifestMatch findMatch(JsonNode manifest, String sampleId) {
        for (JsonNode match : manifest.path("primaryMatches")) {
            if (sampleId.equals(text(match, "sampleId"))) {
                return new ManifestMatch(
                        MatchKind.PRIMARY,
                        text(match.path("providerReferences").path("highlightly"), "matchId"));
            }
        }
        for (JsonNode match : manifest.path("reserveMatches")) {
            if (sampleId.equals(text(match, "sampleId"))) {
                return new ManifestMatch(MatchKind.RESERVE, text(match, "highlightlyMatchId"));
            }
        }
        for (JsonNode match : manifest.path("transversalControls")) {
            if (sampleId.equals(text(match, "sampleId"))) {
                return new ManifestMatch(
                        MatchKind.CONTROL,
                        text(match.path("highlightlyObservation"), "matchId"));
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? "" : value.asText();
    }

    private enum MatchKind {
        PRIMARY,
        RESERVE,
        CONTROL
    }

    private record ManifestMatch(MatchKind kind, String highlightlyMatchId) {
    }
}
