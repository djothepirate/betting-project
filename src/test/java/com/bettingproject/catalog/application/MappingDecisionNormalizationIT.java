package com.bettingproject.catalog.application;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.application.StoredProviderMapping;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///betting",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER",
                "betting.operator.id=test-operator"
        })
@ActiveProfiles("control-api")
@Transactional
@Import(MappingDecisionNormalizationIT.AuthorityConfiguration.class)
class MappingDecisionNormalizationIT {

    private static final String PROVIDER = "highlightly";
    private static final String PROVIDER_COMPETITION_ID = "hly-upl";
    private static final String PROVIDER_HOME_TEAM_ID = "hly-hirnyk";
    private static final String PROVIDER_AWAY_TEAM_ID = "hly-livyi-bereh";
    private static final String SEASON = "2026/2027";
    private static final String PHASE = "REGULAR_SEASON";

    @Autowired
    private CatalogCommandService catalogCommandService;

    @Autowired
    private ProviderMappingRepository mappingRepository;

    @Autowired
    private CalendarNormalizationService normalizationService;

    @Autowired
    private MappingDecisionService mappingDecisionService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void humanRejectionResolvesMissingMappingAndKeepsRejectedMappingAcrossReplay()
            throws IOException {
        UUID competitionId = catalogCommandService.registerCompetition(
                "Ukrainian Premier League", "UKR", CompetitionType.DOMESTIC_LEAGUE);
        UUID awayTeamId = catalogCommandService.registerTeam("FC Livyi Bereh Kyiv", "UKR");
        insertMapping(ProviderMapping.confirmed(
                PROVIDER, ProviderEntityType.COMPETITION, PROVIDER_COMPETITION_ID,
                competitionId, SEASON, PHASE, Instant.parse("2026-09-01T10:00:00Z")));
        insertMapping(ProviderMapping.confirmed(
                PROVIDER, ProviderEntityType.TEAM, PROVIDER_AWAY_TEAM_ID,
                awayTeamId, "", "", Instant.parse("2026-09-01T10:00:00Z")));
        RawSnapshot snapshot = RawSnapshot.capture(
                PROVIDER,
                "calendar/upl",
                Instant.parse("2026-08-11T10:10:01Z"),
                fixture("/fixtures/cat001/calendar-v2-hirnyk-ambiguous.json"),
                "cal01-replay-2");

        NormalizationResult missing = normalizationService.normalize(snapshot);
        UUID missingAnomalyId = singleUuid("""
                SELECT id
                FROM normalization_anomaly
                WHERE anomaly_code = 'MISSING_MAPPING'
                """);
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER, ProviderEntityType.TEAM, PROVIDER_HOME_TEAM_ID, "", "");

        MappingDecisionResult decisionResult = mappingDecisionService.decide(
                MappingDecisionCommand.reject(
                        key,
                        0L,
                        "mapping-reject-hirnyk-e2e",
                        "Provider team identity rejected after manual review"));

        assertThat(missing.fixturesBlocked()).isEqualTo(1);
        assertThat(missing.anomalies()).isEqualTo(1);
        assertThat(decisionResult).isInstanceOf(MappingDecisionResult.Applied.class);
        MappingDecisionResult.Applied applied = (MappingDecisionResult.Applied) decisionResult;
        assertThat(applied.correlatedAnomalyIds()).containsExactly(missingAnomalyId);
        assertThat(applied.replayRequestIds()).hasSize(1);
        UUID replayRequestId = applied.replayRequestIds().getFirst();
        UUID mappingId = applied.decision().providerMappingId();
        UUID receiptId = applied.decision().controlCommandReceiptId();
        UUID decisionId = applied.decision().id();

        NormalizationResult rejectedOpened = normalizationService.normalize(snapshot);
        UUID rejectedAnomalyId = singleUuid("""
                SELECT id
                FROM normalization_anomaly
                WHERE anomaly_code = 'REJECTED_MAPPING'
                """);
        NormalizationResult rejectedObserved = normalizationService.normalize(snapshot);

        assertThat(rejectedOpened.fixturesBlocked()).isEqualTo(1);
        assertThat(rejectedOpened.anomalies()).isEqualTo(1);
        assertThat(rejectedObserved.fixturesBlocked()).isEqualTo(1);
        assertThat(rejectedObserved.anomalies()).isEqualTo(1);
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE id = '%s'
                """.formatted(missingAnomalyId))).isEqualTo("RESOLVED");
        assertThat(anomalyEventTypes(missingAnomalyId)).containsExactly("OPENED", "RESOLVED");
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE id = '%s'
                """.formatted(rejectedAnomalyId))).isEqualTo("OPEN");
        assertThat(singleLong("""
                SELECT occurrence_count
                FROM normalization_anomaly
                WHERE id = '%s'
                """.formatted(rejectedAnomalyId))).isEqualTo(2);
        assertThat(anomalyEventTypes(rejectedAnomalyId)).containsExactly("OPENED", "OBSERVED");

        assertThat(singleUuid("""
                SELECT id
                FROM provider_mapping
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'hly-hirnyk'
                  AND season = ''
                  AND phase = ''
                """)).isEqualTo(mappingId);
        assertThat(singleString("""
                SELECT mapping_status
                FROM provider_mapping
                WHERE id = '%s'
                """.formatted(mappingId))).isEqualTo("REJECTED");
        assertThat(singleLong("""
                SELECT version
                FROM provider_mapping
                WHERE id = '%s'
                """.formatted(mappingId))).isEqualTo(1);
        assertThat(singleLong("""
                SELECT COUNT(*)
                FROM provider_mapping
                WHERE provider = 'highlightly'
                  AND entity_type = 'TEAM'
                  AND provider_entity_id = 'hly-hirnyk'
                """)).isEqualTo(1);

        assertThat(singleLong("""
                SELECT COUNT(*)
                FROM control_command_receipt
                WHERE command_type = 'MAPPING_REJECT'
                """)).isEqualTo(1);
        assertThat(singleLong("""
                SELECT COUNT(*)
                FROM control_command_receipt
                WHERE command_type = 'REPLAY_REQUEST'
                """)).isEqualTo(1);
        assertThat(singleLong("SELECT COUNT(*) FROM control_command_receipt")).isEqualTo(2);
        assertThat(singleUuid("""
                SELECT id
                FROM control_command_receipt
                WHERE command_type = 'MAPPING_REJECT'
                """)).isEqualTo(receiptId);
        assertThat(singleLong("SELECT COUNT(*) FROM normalization_replay_request"))
                .isEqualTo(1);
        assertThat(singleUuid("SELECT id FROM normalization_replay_request"))
                .isEqualTo(replayRequestId);
        assertThat(singleString("SELECT status FROM normalization_replay_request"))
                .isEqualTo("PENDING");
        assertThat(singleString("SELECT origin FROM normalization_replay_request"))
                .isEqualTo("MAPPING_DECISION");
        assertThat(singleUuid("""
                SELECT provider_mapping_decision_id
                FROM normalization_replay_request
                """)).isEqualTo(decisionId);
        assertThat(singleString("""
                SELECT receipt.idempotency_key
                FROM normalization_replay_request AS request
                JOIN control_command_receipt AS receipt
                  ON receipt.id = request.control_command_receipt_id
                """)).isEqualTo("mapping-decision:%s:snapshot:%s".formatted(
                        decisionId,
                        missing.snapshotId()));
        assertThat(singleLong("SELECT COUNT(*) FROM normalization_replay_attempt"))
                .isZero();
        assertThat(singleLong("SELECT COUNT(*) FROM provider_mapping_decision")).isEqualTo(1);
        assertThat(singleUuid("SELECT id FROM provider_mapping_decision")).isEqualTo(decisionId);
        assertThat(singleLong("SELECT COUNT(*) FROM provider_mapping_decision_anomaly"))
                .isEqualTo(1);
        assertThat(singleUuid("""
                SELECT normalization_anomaly_id
                FROM provider_mapping_decision_anomaly
                """)).isEqualTo(missingAnomalyId);
    }

    private void insertMapping(ProviderMapping candidate) {
        StoredProviderMapping stored = mappingRepository.insertIfAbsentAndResolve(candidate);
        assertThat(stored.mapping().status()).isEqualTo(candidate.status());
        assertThat(stored.mapping().canonicalEntityId()).isEqualTo(candidate.canonicalEntityId());
    }

    private byte[] fixture(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test fixture: " + path);
            }
            return input.readAllBytes();
        }
    }

    private List<String> anomalyEventTypes(UUID anomalyId) {
        return jdbcClient.sql("""
                SELECT event_type
                FROM normalization_anomaly_event
                WHERE normalization_anomaly_id = :anomalyId
                ORDER BY created_at, id
                """)
                .param("anomalyId", anomalyId)
                .query(String.class)
                .list();
    }

    private long singleLong(String sql) {
        return jdbcClient.sql(sql).query(Long.class).single();
    }

    private String singleString(String sql) {
        return jdbcClient.sql(sql).query(String.class).single();
    }

    private UUID singleUuid(String sql) {
        return jdbcClient.sql(sql).query(UUID.class).single();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthorityConfiguration {

        @Bean
        @Primary
        CalendarAuthorityPolicy mappingDecisionNormalizationAuthorityPolicy() {
            return new ConfiguredCalendarAuthorityPolicy(
                    "cat-002-lot5-reject-e2e-policy-v1",
                    List.of(new CalendarAuthorityAssignment(
                            new CalendarAuthorityKey(
                                    PROVIDER,
                                    PROVIDER_COMPETITION_ID,
                                    SEASON,
                                    PHASE,
                                    CalendarAuthorityDataType.CALENDAR),
                            CalendarAuthorityRole.PRIMARY)));
        }
    }
}
