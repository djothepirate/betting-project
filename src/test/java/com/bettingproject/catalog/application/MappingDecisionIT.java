package com.bettingproject.catalog.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class MappingDecisionIT {

    private static final String PROVIDER = "synthetic-decision-provider";
    private static final String SEASON = "2026/2027";
    private static final String PHASE = "REGULAR";

    @Autowired
    private MappingDecisionService service;

    @Autowired
    private CatalogCommandService catalogCommandService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void confirmationIsAtomicIdempotentVersionedAndCorrelatesOnlyMappingAnomalies() {
        UUID canonicalTeamId = catalogCommandService.registerTeam(
                "Synthetic Decision FC", "FRA");
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "synthetic-team-42",
                SEASON,
                PHASE);
        UUID mappingAnomalyId = insertAnomaly(key, "MISSING_MAPPING");
        UUID unrelatedAnomalyId = insertAnomaly(key, "INVALID_FIXTURE");
        MappingDecisionCommand firstCommand = MappingDecisionCommand.confirm(
                key,
                canonicalTeamId,
                0L,
                "mapping-confirm-pg-first",
                "Validated manually; token=fake-placeholder-value");

        MappingDecisionResult firstResult = service.decide(firstCommand);
        MappingDecisionResult repeatedResult = service.decide(firstCommand);
        MappingDecisionResult secondResult = service.decide(MappingDecisionCommand.confirm(
                key,
                canonicalTeamId,
                1L,
                "mapping-confirm-pg-second",
                "Validated a second time"));

        assertThat(firstResult).isInstanceOf(MappingDecisionResult.Applied.class);
        assertThat(repeatedResult).isInstanceOf(MappingDecisionResult.AlreadyApplied.class);
        assertThat(secondResult).isInstanceOf(MappingDecisionResult.Applied.class);
        MappingDecisionResult.Applied first = (MappingDecisionResult.Applied) firstResult;
        MappingDecisionResult.AlreadyApplied repeated =
                (MappingDecisionResult.AlreadyApplied) repeatedResult;
        MappingDecisionResult.Applied second = (MappingDecisionResult.Applied) secondResult;
        assertThat(repeated.decision().id()).isEqualTo(first.decision().id());
        assertThat(first.correlatedAnomalyIds()).containsExactly(mappingAnomalyId);
        assertThat(repeated.correlatedAnomalyIds()).containsExactly(mappingAnomalyId);
        assertThat(second.correlatedAnomalyIds()).containsExactly(mappingAnomalyId);
        assertThat(first.replayRequestIds()).hasSize(1);
        assertThat(repeated.replayRequestIds()).isEqualTo(first.replayRequestIds());
        assertThat(second.replayRequestIds()).hasSize(1);
        assertThat(second.replayRequestIds())
                .doesNotContainAnyElementsOf(first.replayRequestIds());
        assertThat(unrelatedAnomalyId).isNotEqualTo(mappingAnomalyId);
        assertThat(first.decision().resultingVersion()).isEqualTo(1);
        assertThat(second.decision().resultingVersion()).isEqualTo(2);
        assertThat(singleLong("SELECT version FROM provider_mapping")).isEqualTo(2);
        assertThat(count("provider_mapping")).isEqualTo(1);
        assertThat(receiptCount("MAPPING_CONFIRM")).isEqualTo(2);
        assertThat(receiptCount("REPLAY_REQUEST")).isEqualTo(2);
        assertThat(count("control_command_receipt")).isEqualTo(4);
        assertThat(count("normalization_replay_request")).isEqualTo(2);
        assertThat(singleLong("""
                SELECT COUNT(*)
                FROM normalization_replay_request
                WHERE status = 'PENDING'
                  AND origin = 'MAPPING_DECISION'
                """)).isEqualTo(2);
        assertThat(singleLong("""
                SELECT COUNT(DISTINCT provider_mapping_decision_id)
                FROM normalization_replay_request
                """)).isEqualTo(2);
        assertThat(count("provider_mapping_decision")).isEqualTo(2);
        assertThat(count("provider_mapping_decision_anomaly")).isEqualTo(2);
        assertThat(singleString("""
                SELECT justification
                FROM provider_mapping_decision
                WHERE resulting_version = 1
                """))
                .contains("[REDACTED]")
                .doesNotContain("fake-placeholder-value");
        assertThat(singleString("""
                SELECT status
                FROM normalization_anomaly
                WHERE id = '%s'
                """.formatted(mappingAnomalyId))).isEqualTo("OPEN");
    }

    @Test
    void sameIdempotencyKeyWithDifferentCommandDoesNotCreateAnAuditRecord() {
        UUID canonicalTeamId = catalogCommandService.registerTeam(
                "Synthetic Conflict FC", "FRA");
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "synthetic-conflict-team",
                SEASON,
                PHASE);
        service.decide(MappingDecisionCommand.confirm(
                key,
                canonicalTeamId,
                0L,
                "mapping-global-key",
                "Initial decision"));

        MappingDecisionResult conflicting = service.decide(MappingDecisionCommand.reject(
                key,
                1L,
                "mapping-global-key",
                "Different decision"));

        assertThat(conflicting).isInstanceOf(MappingDecisionResult.IdempotencyConflict.class);
        assertThat(count("provider_mapping")).isEqualTo(1);
        assertThat(count("control_command_receipt")).isEqualTo(1);
        assertThat(count("provider_mapping_decision")).isEqualTo(1);
        assertThat(singleString("SELECT mapping_status FROM provider_mapping"))
                .isEqualTo("CONFIRMED");
        assertThat(singleLong("SELECT version FROM provider_mapping")).isEqualTo(1);
    }

    @Test
    void rejectionCanCreateAnAuditedMappingWithoutCanonicalTarget() {
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "synthetic-rejected-team",
                "",
                "");

        MappingDecisionResult result = service.decide(MappingDecisionCommand.reject(
                key,
                0L,
                "mapping-reject-pg",
                "No reliable identity"));

        assertThat(result).isInstanceOf(MappingDecisionResult.Applied.class);
        assertThat(singleString("SELECT mapping_status FROM provider_mapping"))
                .isEqualTo("REJECTED");
        assertThat(singleLong("SELECT version FROM provider_mapping")).isEqualTo(1);
        assertThat(singleBoolean("""
                SELECT canonical_entity_id IS NULL AND confidence IS NULL
                FROM provider_mapping
                """)).isTrue();
        assertThat(singleString("SELECT decision_type FROM provider_mapping_decision"))
                .isEqualTo("REJECT");
    }

    @Test
    void staleVersionLeavesTheMappingReceiptHistoryAndCorrelationsUntouched() {
        UUID canonicalTeamId = catalogCommandService.registerTeam(
                "Synthetic Stale Version FC", "FRA");
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "synthetic-stale-team",
                SEASON,
                PHASE);
        insertAnomaly(key, "MISSING_MAPPING");
        MappingDecisionResult initialResult = service.decide(MappingDecisionCommand.confirm(
                key,
                canonicalTeamId,
                0L,
                "mapping-stale-initial",
                "Initial confirmation"));

        MappingDecisionResult stale = service.decide(MappingDecisionCommand.reject(
                key,
                0L,
                "mapping-stale-reject",
                "Stale rejection"));

        assertThat(stale).isEqualTo(new MappingDecisionResult.VersionConflict(0, 1L));
        assertThat(initialResult).isInstanceOf(MappingDecisionResult.Applied.class);
        MappingDecisionResult.Applied initial = (MappingDecisionResult.Applied) initialResult;
        assertThat(initial.replayRequestIds()).hasSize(1);
        assertThat(singleString("SELECT mapping_status FROM provider_mapping"))
                .isEqualTo("CONFIRMED");
        assertThat(singleLong("SELECT version FROM provider_mapping")).isEqualTo(1);
        assertThat(receiptCount("MAPPING_CONFIRM")).isEqualTo(1);
        assertThat(receiptCount("REPLAY_REQUEST")).isEqualTo(1);
        assertThat(count("control_command_receipt")).isEqualTo(2);
        assertThat(count("normalization_replay_request")).isEqualTo(1);
        assertThat(singleString("SELECT status FROM normalization_replay_request"))
                .isEqualTo("PENDING");
        assertThat(singleUuid("SELECT id FROM normalization_replay_request"))
                .isEqualTo(initial.replayRequestIds().getFirst());
        assertThat(count("provider_mapping_decision")).isEqualTo(1);
        assertThat(count("provider_mapping_decision_anomaly")).isEqualTo(1);
    }

    @Test
    void oneDecisionCorrelatesAllExactOpenMappingAnomaliesAcrossDistinctSnapshots() {
        UUID canonicalTeamId = catalogCommandService.registerTeam(
                "Synthetic Multi Anomaly FC", "FRA");
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "synthetic-multi-anomaly-team",
                SEASON,
                PHASE);
        UUID missing = insertAnomaly(key, "MISSING_MAPPING");
        UUID ambiguous = insertAnomaly(key, "AMBIGUOUS_MAPPING");
        UUID rejected = insertAnomaly(key, "REJECTED_MAPPING");

        MappingDecisionCommand command = MappingDecisionCommand.confirm(
                key,
                canonicalTeamId,
                0L,
                "mapping-multi-anomaly",
                "Resolved all exact mapping anomalies");
        MappingDecisionResult result = service.decide(command);
        MappingDecisionResult repeatedResult = service.decide(command);

        assertThat(result).isInstanceOf(MappingDecisionResult.Applied.class);
        assertThat(repeatedResult).isInstanceOf(MappingDecisionResult.AlreadyApplied.class);
        MappingDecisionResult.Applied applied = (MappingDecisionResult.Applied) result;
        MappingDecisionResult.AlreadyApplied repeated =
                (MappingDecisionResult.AlreadyApplied) repeatedResult;
        assertThat(applied.correlatedAnomalyIds())
                .containsExactlyInAnyOrder(missing, ambiguous, rejected);
        assertThat(repeated.replayRequestIds()).containsExactlyElementsOf(
                applied.replayRequestIds());
        assertThat(applied.replayRequestIds()).hasSize(3);
        assertThat(count("provider_mapping_decision_anomaly")).isEqualTo(3);
        assertThat(count("normalization_anomaly")).isEqualTo(3);
        assertThat(jdbcClient.sql("""
                SELECT COUNT(*)
                FROM normalization_anomaly
                WHERE status = 'OPEN'
                """).query(Long.class).single()).isEqualTo(3);
        assertThat(jdbcClient.sql("""
                SELECT COUNT(DISTINCT raw_snapshot_id)
                FROM normalization_anomaly
                """).query(Long.class).single()).isEqualTo(3);
    }

    @Test
    void decisionWithoutMatchingAnomalyRemainsValidWithoutCorrelation() {
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "synthetic-no-anomaly-team",
                "",
                "");

        MappingDecisionResult result = service.decide(MappingDecisionCommand.reject(
                key,
                0L,
                "mapping-without-anomaly",
                "No correlated snapshot exists"));

        assertThat(result).isInstanceOf(MappingDecisionResult.Applied.class);
        assertThat(((MappingDecisionResult.Applied) result).correlatedAnomalyIds()).isEmpty();
        assertThat(count("provider_mapping")).isEqualTo(1);
        assertThat(count("provider_mapping_decision")).isEqualTo(1);
        assertThat(count("provider_mapping_decision_anomaly")).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void receiptFailureRollsBackTheMappingBeforeHistoryCreation() {
        UUID canonicalTeamId = catalogCommandService.registerTeam(
                "Synthetic Receipt Rollback FC", "FRA");
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "synthetic-receipt-rollback",
                SEASON,
                PHASE);
        installRejectingTrigger(
                "control_command_receipt",
                "cat002_test_reject_control_receipt");

        try {
            assertThatThrownBy(() -> service.decide(MappingDecisionCommand.confirm(
                    key,
                    canonicalTeamId,
                    0L,
                    "mapping-receipt-rollback",
                    "Receipt rollback review")))
                    .isInstanceOf(DataAccessException.class);

            assertThat(count("provider_mapping")).isZero();
            assertThat(count("control_command_receipt")).isZero();
            assertThat(count("provider_mapping_decision")).isZero();
            assertThat(count("provider_mapping_decision_anomaly")).isZero();
        }
        finally {
            removeRejectingTrigger(
                    "control_command_receipt",
                    "cat002_test_reject_control_receipt");
            deleteCommittedDecisionTestData();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void journalFailureRollsBackTheMappingAndReceipt() {
        UUID canonicalTeamId = catalogCommandService.registerTeam(
                "Synthetic Journal Rollback FC", "FRA");
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "synthetic-journal-rollback",
                SEASON,
                PHASE);
        installRejectingTrigger(
                "provider_mapping_decision",
                "cat002_test_reject_mapping_decision");

        try {
            assertThatThrownBy(() -> service.decide(MappingDecisionCommand.confirm(
                    key,
                    canonicalTeamId,
                    0L,
                    "mapping-journal-rollback",
                    "Journal rollback review")))
                    .isInstanceOf(DataAccessException.class);

            assertThat(count("provider_mapping")).isZero();
            assertThat(count("control_command_receipt")).isZero();
            assertThat(count("provider_mapping_decision")).isZero();
            assertThat(count("provider_mapping_decision_anomaly")).isZero();
        }
        finally {
            removeRejectingTrigger(
                    "provider_mapping_decision",
                    "cat002_test_reject_mapping_decision");
            deleteCommittedDecisionTestData();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void correlationFailureRollsBackTheMappingReceiptAndDecision() {
        UUID canonicalTeamId = catalogCommandService.registerTeam(
                "Synthetic Correlation Rollback FC", "FRA");
        ProviderMappingKey key = new ProviderMappingKey(
                PROVIDER,
                ProviderEntityType.TEAM,
                "synthetic-correlation-rollback",
                SEASON,
                PHASE);
        insertAnomaly(key, "MISSING_MAPPING");
        installRejectingTrigger(
                "provider_mapping_decision_anomaly",
                "cat002_test_reject_decision_anomaly");

        try {
            assertThatThrownBy(() -> service.decide(MappingDecisionCommand.confirm(
                    key,
                    canonicalTeamId,
                    0L,
                    "mapping-correlation-rollback",
                    "Correlation rollback review")))
                    .isInstanceOf(DataAccessException.class);

            assertThat(count("provider_mapping")).isZero();
            assertThat(count("control_command_receipt")).isZero();
            assertThat(count("provider_mapping_decision")).isZero();
            assertThat(count("provider_mapping_decision_anomaly")).isZero();
            assertThat(count("normalization_anomaly")).isEqualTo(1);
            assertThat(singleString("SELECT status FROM normalization_anomaly"))
                    .isEqualTo("OPEN");
        }
        finally {
            removeRejectingTrigger(
                    "provider_mapping_decision_anomaly",
                    "cat002_test_reject_decision_anomaly");
            deleteCommittedDecisionTestData();
        }
    }

    private UUID insertAnomaly(ProviderMappingKey key, String anomalyCode) {
        UUID snapshotId = UUID.randomUUID();
        UUID anomalyId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-01T18:30:00Z");
        jdbcClient.sql("""
                INSERT INTO raw_snapshot (
                    id, provider, endpoint, received_at, payload_sha256,
                    payload_compression, payload, connector_version
                ) VALUES (
                    :id, :provider, :endpoint, :receivedAt, :payloadSha256,
                    'NONE', :payload, 'test-only'
                )
                """)
                .param("id", snapshotId)
                .param("provider", key.provider())
                .param("endpoint", "calendar/test/" + anomalyCode)
                .param("receivedAt", now.atOffset(ZoneOffset.UTC))
                .param("payloadSha256", String.format("%064x", snapshotId.getLeastSignificantBits()))
                .param("payload", "{}".getBytes())
                .update();
        jdbcClient.sql("""
                INSERT INTO normalization_anomaly (
                    id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                    season, phase, anomaly_code, details, status,
                    created_at, version, last_seen_at, updated_at, occurrence_count
                ) VALUES (
                    :id, :snapshotId, :provider, :entityType, :providerEntityId,
                    :season, :phase, :anomalyCode, 'Synthetic test anomaly', 'OPEN',
                    :createdAt, 1, :createdAt, :createdAt, 1
                )
                """)
                .param("id", anomalyId)
                .param("snapshotId", snapshotId)
                .param("provider", key.provider())
                .param("entityType", key.entityType().name())
                .param("providerEntityId", key.providerEntityId())
                .param("season", key.season())
                .param("phase", key.phase())
                .param("anomalyCode", anomalyCode)
                .param("createdAt", now.atOffset(ZoneOffset.UTC))
                .update();
        return anomalyId;
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                .query(Long.class)
                .single();
    }

    private long receiptCount(String commandType) {
        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM control_command_receipt
                WHERE command_type = :commandType
                """)
                .param("commandType", commandType)
                .query(Long.class)
                .single();
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

    private boolean singleBoolean(String sql) {
        return jdbcClient.sql(sql).query(Boolean.class).single();
    }

    private void installRejectingTrigger(String table, String functionName) {
        jdbcClient.sql("""
                CREATE OR REPLACE FUNCTION %s()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'forced CAT-002 decision persistence failure';
                END;
                $$
                """.formatted(functionName)).update();
        jdbcClient.sql("""
                CREATE TRIGGER %s
                BEFORE INSERT ON %s
                FOR EACH ROW
                EXECUTE FUNCTION %s()
                """.formatted(functionName, table, functionName)).update();
    }

    private void removeRejectingTrigger(String table, String functionName) {
        jdbcClient.sql("DROP TRIGGER IF EXISTS %s ON %s".formatted(functionName, table))
                .update();
        jdbcClient.sql("DROP FUNCTION IF EXISTS %s()".formatted(functionName)).update();
    }

    private void deleteCommittedDecisionTestData() {
        jdbcClient.sql("DELETE FROM provider_mapping_decision_anomaly").update();
        jdbcClient.sql("DELETE FROM provider_mapping_decision").update();
        jdbcClient.sql("DELETE FROM control_command_receipt").update();
        jdbcClient.sql("DELETE FROM provider_mapping WHERE provider = :provider")
                .param("provider", PROVIDER)
                .update();
        jdbcClient.sql("DELETE FROM normalization_anomaly_event").update();
        jdbcClient.sql("DELETE FROM normalization_anomaly WHERE provider = :provider")
                .param("provider", PROVIDER)
                .update();
        jdbcClient.sql("DELETE FROM raw_snapshot WHERE provider = :provider")
                .param("provider", PROVIDER)
                .update();
        jdbcClient.sql("""
                DELETE FROM canonical_team
                WHERE canonical_name LIKE 'Synthetic % Rollback FC'
                """).update();
    }
}
