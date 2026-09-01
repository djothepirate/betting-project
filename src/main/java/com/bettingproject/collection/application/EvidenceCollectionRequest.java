package com.bettingproject.collection.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EvidenceCollectionRequest(
        String sampleId,
        String providerMatchId,
        CollectionEndpoint endpoint,
        String collectionWindow,
        List<String> scenarios) {

    private static final Set<String> ALLOWED_SCENARIOS = Set.of(
            "ID-01", "TIM-01", "OPS-01", "REP-01",
            "LIN-01", "STA-01", "EVT-01", "PLY-01");

    public EvidenceCollectionRequest {
        sampleId = requirePattern(sampleId, "sampleId", "ENR-[PCR][0-9]{2}");
        providerMatchId = requirePattern(providerMatchId, "providerMatchId", "[0-9]+");
        endpoint = Objects.requireNonNull(endpoint, "endpoint");
        collectionWindow = requirePattern(collectionWindow, "collectionWindow", "[A-Z0-9+_-]+");
        scenarios = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(scenarios, "scenarios")));
        if (scenarios.isEmpty() || scenarios.stream().anyMatch(scenario -> !ALLOWED_SCENARIOS.contains(scenario))) {
            throw new IllegalArgumentException("scenarios must contain only ENR-001 scenario identifiers");
        }
    }

    private static String requirePattern(String value, String name, String pattern) {
        if (value == null || !value.matches(pattern)) {
            throw new IllegalArgumentException(name + " has an invalid format");
        }
        return value;
    }
}
