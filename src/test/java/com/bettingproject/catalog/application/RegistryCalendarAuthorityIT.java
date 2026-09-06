package com.bettingproject.catalog.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.collection.application.capability.CapabilityTestSamples;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///betting_mvp001",
        "spring.datasource.username=test", "spring.datasource.password=test",
        "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER"
})
@ActiveProfiles("batch-worker")
@Transactional
@Import(RegistryCalendarAuthorityIT.SyntheticRegistry.class)
class RegistryCalendarAuthorityIT {
    @Autowired private CalendarNormalizationService normalizer;
    @Autowired private CalendarAuthorityPolicy policy;
    @Autowired private ProviderCapabilityRegistry registry;
    @Autowired private CatalogCommandService commands;
    @Autowired private ProviderMappingRepository mappings;
    @Autowired private JdbcClient jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"PPL", "PD", "DED", "ELC"})
    void registryPrimaryCreatesAndConflictingControlNeverChangesFactsOrWatermark(String code) {
        assertThat(policy).isInstanceOf(RegistryCalendarAuthorityPolicy.class);
        bind(code);
        var first = normalizer.normalize(snapshot("synthetic-primary", code, "2026/2027", "REGULAR_SEASON", "SCHEDULED"));
        assertThat(first.fixturesCreated()).isEqualTo(1);
        UUID authority = jdbc.sql("SELECT last_authority_observation_id FROM canonical_fixture").query(UUID.class).single();
        assertThat(string("SELECT last_authority_policy_version FROM canonical_fixture")).isEqualTo(registry.documentSha256());
        assertThat(string("SELECT policy_version FROM fixture_application_log")).isEqualTo(registry.documentSha256());
        assertThat(string("SELECT authority_role FROM fixture_application_log")).isEqualTo("PRIMARY");

        var control = normalizer.normalize(snapshot("synthetic-control", code, "2026/2027", "REGULAR_SEASON", "CANCELLED"));
        assertThat(control.fixturesBlocked()).isEqualTo(1);
        assertThat(string("SELECT status FROM canonical_fixture")).isEqualTo("SCHEDULED");
        assertThat(jdbc.sql("SELECT last_authority_observation_id FROM canonical_fixture").query(UUID.class).single())
                .isEqualTo(authority);
        assertThat(string("SELECT last_authority_provider FROM canonical_fixture")).isEqualTo("synthetic-primary");
        assertThat(jdbc.sql("SELECT count(*) FROM fixture_application_log WHERE outcome='CONTROL_DIVERGENCE' AND authority_role='CONTROL'")
                .query(Long.class).single()).isEqualTo(1L);
        assertThat(string("SELECT anomaly_code FROM normalization_anomaly")).isEqualTo("CONTROL_DIVERGENCE");
        assertThat(jdbc.sql("SELECT count(*) FROM fixture_application_log WHERE policy_version=:version")
                .param("version", registry.documentSha256()).query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void inactiveEntryPreservesEvidenceWithoutInventingCanonicalState() {
        assertUnassigned(snapshot("synthetic-inactive", "PPL", "2026/2027", "REGULAR_SEASON", "SCHEDULED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"season", "phase"})
    void noFallbackToAnotherSeasonOrPhase(String field) {
        assertUnassigned(snapshot("synthetic-primary", "PPL", field.equals("season") ? "2027/2028" : "2026/2027",
                field.equals("phase") ? "FINAL" : "REGULAR_SEASON", "SCHEDULED"));
    }

    @Test
    void controlWithoutPrimaryRemainsBlocked() {
        bind("PPL");
        var result = normalizer.normalize(snapshot("synthetic-control", "PPL", "2026/2027", "REGULAR_SEASON", "SCHEDULED"));
        assertThat(result.fixturesBlocked()).isEqualTo(1);
        assertThat(string("SELECT anomaly_code FROM normalization_anomaly")).isEqualTo("CONTROL_WITHOUT_PRIMARY");
        assertThat(count("canonical_fixture")).isZero();
    }

    @ParameterizedTest
    @MethodSource("wildcards")
    void allRuntimeWildcardsArePreservedAsUnassigned(String field, String marker) {
        String provider = "synthetic-primary";
        String code = "PPL";
        String season = "2026/2027";
        String phase = "REGULAR_SEASON";
        switch (field) {
            case "provider" -> provider += marker;
            case "competition" -> code += marker;
            case "season" -> season += marker;
            case "phase" -> phase += marker;
            default -> throw new IllegalArgumentException();
        }
        assertUnassigned(snapshot(provider, code, season, phase, "SCHEDULED"));
    }

    private static Stream<Arguments> wildcards() {
        return List.of("provider", "competition", "season", "phase").stream()
                .flatMap(field -> List.of("*", "?", "%").stream().map(marker -> Arguments.of(field, marker)));
    }

    private void assertUnassigned(RawSnapshot snapshot) {
        var result = normalizer.normalize(snapshot);
        assertThat(result.compatible()).isTrue();
        assertThat(result.fixturesBlocked()).isEqualTo(1);
        assertThat(result.anomalies()).isEqualTo(1);
        assertThat(count("raw_snapshot")).isEqualTo(1);
        assertThat(count("fixture_observation")).isEqualTo(1);
        assertThat(count("fixture_application_log")).isEqualTo(1);
        assertThat(count("canonical_season")).isZero();
        assertThat(count("canonical_fixture")).isZero();
        assertThat(count("provider_mapping")).isZero();
        assertThat(string("SELECT outcome FROM fixture_application_log")).isEqualTo("UNASSIGNED");
        assertThat(string("SELECT policy_version FROM fixture_application_log")).isEqualTo(registry.documentSha256());
        assertThat(string("SELECT anomaly_code FROM normalization_anomaly")).isEqualTo("UNASSIGNED_AUTHORITY");
        assertThat(jdbc.sql("SELECT payload FROM raw_snapshot").query(byte[].class).single()).isEqualTo(snapshot.payload());
    }

    private void bind(String code) {
        UUID competition = commands.registerCompetition("Synthetic " + code, "PRT", CompetitionType.DOMESTIC_LEAGUE);
        UUID home = commands.registerTeam("Synthetic Home", "PRT");
        UUID away = commands.registerTeam("Synthetic Away", "PRT");
        for (String provider : List.of("synthetic-primary", "synthetic-control")) {
            mappings.insertIfAbsentAndResolve(ProviderMapping.confirmed(provider, ProviderEntityType.COMPETITION,
                    "synthetic-" + code.toLowerCase(java.util.Locale.ROOT), competition, "2026/2027", "REGULAR_SEASON", Instant.EPOCH));
            mappings.insertIfAbsentAndResolve(ProviderMapping.confirmed(provider, ProviderEntityType.TEAM,
                    "home", home, "", "", Instant.EPOCH));
            mappings.insertIfAbsentAndResolve(ProviderMapping.confirmed(provider, ProviderEntityType.TEAM,
                    "away", away, "", "", Instant.EPOCH));
        }
    }

    private static RawSnapshot snapshot(String provider, String code, String season, String phase, String status) {
        String json = """
                {"schemaVersion":"cal01-fixture-v3","provider":"%s","observedAt":"2026-09-06T10:00:00Z",
                 "fixtures":[{"providerFixtureId":"synthetic-match","competition":{
                   "providerCompetitionId":"synthetic-%s","name":"Synthetic competition","countryCode":"PRT",
                   "type":"DOMESTIC_LEAGUE","season":"%s","phase":"%s"},
                 "kickoff":"2026-09-10T12:00:00Z","status":"%s","neutralVenue":null,"participantsUnordered":false,
                 "homeTeam":{"providerTeamId":"home","name":"Synthetic Home","countryCode":"PRT"},
                 "awayTeam":{"providerTeamId":"away","name":"Synthetic Away","countryCode":"PRT"}}]}
                """.formatted(provider, code.toLowerCase(java.util.Locale.ROOT), season, phase, status);
        return RawSnapshot.capture(provider, "/synthetic/calendar", Instant.parse("2026-09-06T10:00:01Z"),
                json.getBytes(StandardCharsets.UTF_8), "synthetic-registry-it-v1");
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private String string(String sql) {
        return jdbc.sql(sql).query(String.class).single();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SyntheticRegistry {
        @Bean
        @Primary
        ProviderCapabilityRegistry syntheticRegistry() {
            return CapabilityTestSamples.registry();
        }
    }
}
