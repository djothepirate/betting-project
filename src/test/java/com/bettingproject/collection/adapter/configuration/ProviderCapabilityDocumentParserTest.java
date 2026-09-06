package com.bettingproject.collection.adapter.configuration;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.bettingproject.collection.application.capability.CapabilityTestSamples.*;
import static org.assertj.core.api.Assertions.*;

class ProviderCapabilityDocumentParserTest {
    private final ProviderCapabilityDocumentParser parser = new ProviderCapabilityDocumentParser();

    @Test
    void productionBaselineIsEmptyAndHashCoversExactBytes() throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + ClasspathProviderCapabilityConfiguration.RESOURCE)) {
            byte[] bytes = java.util.Objects.requireNonNull(stream).readAllBytes();
            var registry = parser.parse(bytes);
            assertThat(registry.registryVersion()).isEqualTo("mvp-001-inactive-v1");
            assertThat(registry.candidates(route("PPL"))).isEmpty();
            assertThat(registry.find(key("synthetic-primary", "PPL"))).isEmpty();
            assertThat(registry.documentSha256()).isEqualTo(SnapshotHasher.sha256(bytes));
            byte[] reformatted = (new String(bytes, StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);
            assertThat(parser.parse(reformatted).documentSha256()).isNotEqualTo(registry.documentSha256());
        }
    }

    @Test
    void loadsExplicitSyntheticRoutesAndEvidence() {
        var registry = parse(document());
        var capability = registry.find(key("synthetic-primary", "PD")).orElseThrow();
        assertThat(capability.key().providerCompetitionId()).isEqualTo("synthetic-pd");
        assertThat(capability.route().competitionCode()).isEqualTo("PD");
        assertThat(capability.evidence().getFirst().logicalId()).isEqualTo("SYNTHETIC-PD-PRIMARY");
        assertThat(capability.evidence().getFirst().observedAt()).hasToString("2026-09-06T10:00:00Z");
        assertThat(registry.documentSha256()).hasSize(64);
    }

    @Test
    void rejectsStructuralAndCoercionErrorsWithoutEchoingTheDocument() {
        String valid = document();
        for (String malformed : List.of(
                "null", "[]", valid + " {}", valid + " false", valid + " trailing",
                valid.replace("\"schemaVersion\":", "\"unknown\":1,\"schemaVersion\":"),
                valid.replace("\"schemaVersion\":", "\"schemaVersion\":\"duplicate\",\"schemaVersion\":"),
                valid.replace("provider-capability-registry-v1", "provider-capability-registry-v2"),
                valid.replace("\"enabled\": true", "\"enabled\": \"true\""),
                valid.replace("\"enabled\": true", "\"enabled\": null"),
                valid.replace("\"enabled\": true", "\"enabled\": 1"),
                valid.replace("\"enabled\": true,", ""),
                valid.replace("\"sourceSeason\": \"2026/2027\"", "\"sourceSeason\": 2026"),
                valid.replace("\"provider\":", "\"unknown\":null,\"provider\":"),
                valid.replace("\"provider\":", "\"provider\":\"duplicate\",\"provider\":"),
                valid.replace("2026-09-06T10:00:00Z", "not-an-instant"),
                valid.replace("a".repeat(64), "A".repeat(64)),
                valid.replace("synthetic-primary", " synthetic-primary"))) {
            assertThatIllegalArgumentException().isThrownBy(() -> parse(malformed))
                    .withMessage("invalid provider capability registry document").withNoCause();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "?", "%"})
    void rejectsConfigurationWildcardsInEveryKeyButAllowsRuntimeLiterals(String marker) {
        for (String target : List.of("synthetic-primary", "synthetic-ppl", "2026/2027", "REGULAR_SEASON", "PPL")) {
            assertThatIllegalArgumentException().isThrownBy(() -> parse(document().replace(target, target + marker)));
        }
        var registry = registry();
        var key = key("synthetic-primary", "PPL");
        for (ProviderCapabilityKey runtime : List.of(
                new ProviderCapabilityKey(key.provider() + marker, key.providerCompetitionId(), key.sourceSeason(), key.sourcePhase(), key.dataType()),
                new ProviderCapabilityKey(key.provider(), key.providerCompetitionId() + marker, key.sourceSeason(), key.sourcePhase(), key.dataType()),
                new ProviderCapabilityKey(key.provider(), key.providerCompetitionId(), key.sourceSeason() + marker, key.sourcePhase(), key.dataType()),
                new ProviderCapabilityKey(key.provider(), key.providerCompetitionId(), key.sourceSeason(), key.sourcePhase() + marker, key.dataType()))) {
            assertThat(registry.find(runtime)).isEmpty();
        }
    }

    @Test
    void allSixDataTypesAreExactAndUnknownTypesFail() {
        // Remove CALENDAR_ONLY entries for this generic capability test.
        String generic = document().replace("CALENDAR_ONLY", "PRIMARY");
        generic = generic.replace("\"status\": \"PRIMARY\",\n      \"authorityRole\": \"CONTROL\"",
                "\"status\": \"CONTROL\",\n      \"authorityRole\": \"CONTROL\"");
        for (CapabilityDataType type : CapabilityDataType.values()) {
            var registry = parse(generic.replace("CALENDAR", type.name()));
            assertThat(registry.candidates(new CapabilityRouteKey("PPL", "2026/2027", "REGULAR_SEASON", type)))
                    .hasSize(3);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> parse(document().replace("\"CALENDAR\"", "\"UNKNOWN\"")));
    }

    private com.bettingproject.collection.application.capability.ProviderCapabilityRegistry parse(String document) {
        return parser.parse(document.getBytes(StandardCharsets.UTF_8));
    }
}
