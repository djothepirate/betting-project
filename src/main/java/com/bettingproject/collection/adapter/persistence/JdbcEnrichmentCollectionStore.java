package com.bettingproject.collection.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.application.enrichment.EnrichmentAttemptState;
import com.bettingproject.collection.application.enrichment.EnrichmentCollectionAttempt;
import com.bettingproject.collection.application.enrichment.EnrichmentCollectionContext;
import com.bettingproject.collection.application.enrichment.EnrichmentCollectionStore;
import com.bettingproject.collection.application.enrichment.EnrichmentDerivationRecord;
import com.bettingproject.collection.application.enrichment.EnrichmentDerivationStore;
import com.bettingproject.collection.application.enrichment.EnrichmentProviderResponse;
import com.bettingproject.collection.application.enrichment.StoredEnrichmentAttempt;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.bettingproject.enrichment.domain.EnrichmentPlanStep;
import com.bettingproject.enrichment.domain.EnrichmentPlanStepCode;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter for durable enrichment attempts. It stores logical identifiers, never authenticated URLs. */
@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcEnrichmentCollectionStore implements EnrichmentCollectionStore, EnrichmentDerivationStore {
    private final JdbcClient jdbc;

    public JdbcEnrichmentCollectionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EnrichmentCollectionContext> findContext(UUID admissionId, EnrichmentPlanStepCode stepCode) {
        return jdbc.sql("""
                SELECT plan.id AS plan_id, admission.id AS admission_id,
                    admission.canonical_fixture_id, plan.budget_window_id, plan.registry_sha256,
                    admission.kickoff_at, season.season_label, fixture.phase,
                    authority.provider AS authority_provider,
                    authority.provider_competition_id AS authority_competition_id,
                    authority.source_season AS authority_source_season,
                    authority.source_phase AS authority_source_phase,
                    authority.provider_fixture_id AS authority_provider_fixture_id,
                    step.id AS step_id, step.step_code, step.family_scope, step.scheduled_at,
                    step.condition_code, step.trigger_observed_at, step.trigger_evidence_observation_id
                FROM enrichment_daily_admission admission
                JOIN enrichment_daily_plan plan ON plan.id = admission.plan_id
                JOIN canonical_fixture fixture ON fixture.id = admission.canonical_fixture_id
                JOIN canonical_season season ON season.id = fixture.season_id
                JOIN fixture_observation authority ON authority.id = fixture.last_authority_observation_id
                JOIN enrichment_plan_step step ON step.admission_id = admission.id
                WHERE admission.id = :admission AND step.step_code = :step
                  AND authority.source_season IS NOT NULL
                """)
                .param("admission", admissionId)
                .param("step", stepCode.name())
                .query((rs, row) -> new EnrichmentCollectionContext(
                        rs.getObject("plan_id", UUID.class), rs.getObject("admission_id", UUID.class),
                        rs.getObject("canonical_fixture_id", UUID.class), rs.getObject("budget_window_id", UUID.class),
                        rs.getString("registry_sha256"), instant(rs, "kickoff_at"),
                        "UNRESOLVED", rs.getString("season_label"), rs.getString("phase"),
                        rs.getString("authority_provider"), rs.getString("authority_competition_id"),
                        rs.getString("authority_source_season"), rs.getString("authority_source_phase"),
                        rs.getString("authority_provider_fixture_id"),
                        new EnrichmentPlanStep(rs.getObject("step_id", UUID.class),
                                EnrichmentPlanStepCode.valueOf(rs.getString("step_code")),
                                rs.getString("family_scope"), instant(rs, "scheduled_at"),
                                rs.getString("condition_code"), instant(rs, "trigger_observed_at"),
                                rs.getObject("trigger_evidence_observation_id", UUID.class))))
                .optional()
                .map(context -> withLogicalCompetition(context, admissionId));
    }

    private EnrichmentCollectionContext withLogicalCompetition(EnrichmentCollectionContext context, UUID admissionId) {
        // The logical competition code is recovered from the exact authority calendar capability by the caller.
        // This placeholder must not be used as an inferred canonical identifier.
        return new EnrichmentCollectionContext(context.planId(), context.admissionId(), context.canonicalFixtureId(),
                context.planBudgetWindowId(), context.planRegistrySha256(), context.kickoffAt(),
                "UNRESOLVED", context.logicalSeason(), context.logicalPhase(), context.authorityProvider(),
                context.authorityCompetitionId(), context.authoritySourceSeason(), context.authoritySourcePhase(),
                context.authorityProviderFixtureId(), context.step());
    }

    @Override
    public List<String> confirmedFixtureMappings(UUID canonicalFixtureId, ProviderCapabilityKey capability) {
        return jdbc.sql("""
                SELECT provider_entity_id FROM provider_mapping
                WHERE provider = :provider AND entity_type = 'FIXTURE'
                  AND canonical_entity_id = :canonical AND season = :season AND phase = :phase
                  AND mapping_status = 'CONFIRMED'
                ORDER BY provider_entity_id LIMIT 2
                """)
                .param("provider", capability.provider()).param("canonical", canonicalFixtureId)
                .param("season", capability.sourceSeason()).param("phase", capability.sourcePhase())
                .query(String.class).list();
    }

    @Override
    public boolean budgetWindowMatchesProvider(UUID budgetWindowId, String provider) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM provider_budget_window budget_window
                    JOIN provider_budget_scope budget_scope ON budget_scope.id = budget_window.scope_id
                    WHERE budget_window.id = :window AND budget_scope.provider = :provider
                )
                """)
                .param("window", budgetWindowId).param("provider", provider)
                .query(Boolean.class).single();
    }

    @Override
    @Transactional
    public StoredEnrichmentAttempt createAndResolve(EnrichmentCollectionAttempt candidate) {
        Optional<EnrichmentCollectionAttempt> existing = findAttemptByIntent(candidate.budgetIntentId());
        if (existing.isPresent()) {
            if (!sameRequest(existing.get(), candidate)) {
                throw new IllegalStateException("Enrichment attempt idempotency conflict");
            }
            return new StoredEnrichmentAttempt(existing.get(), false);
        }
        jdbc.sql("""
                INSERT INTO provider_call_audit (id, provider, logical_endpoint, requested_at,
                    received_at, http_status, latency_ms, quota_remaining, payload_sha256,
                    connector_version, error_code, created_at)
                VALUES (:id, :provider, :endpoint, :requested, NULL, NULL, NULL, NULL, NULL,
                    :connector, NULL, :created)
                """)
                .param("id", candidate.auditId()).param("provider", candidate.capability().provider())
                .param("endpoint", candidate.logicalEndpoint()).param("requested", utc(candidate.requestedAt()))
                .param("connector", candidate.connectorVersion()).param("created", utc(candidate.createdAt())).update();
        insertAttempt(candidate);
        return new StoredEnrichmentAttempt(candidate, true);
    }

    @Override
    public Optional<EnrichmentCollectionAttempt> findAttempt(UUID attemptId) {
        return jdbc.sql("SELECT * FROM enrichment_collection_attempt WHERE id = :id")
                .param("id", attemptId).query(this::mapAttempt).optional();
    }

    @Override
    public Optional<EnrichmentCollectionAttempt> findAttemptByIntent(UUID budgetIntentId) {
        return jdbc.sql("SELECT * FROM enrichment_collection_attempt WHERE budget_intent_id = :id")
                .param("id", budgetIntentId).query(this::mapAttempt).optional();
    }

    @Override
    public void markCommitted(UUID attemptId, Instant authorizedAt) {
        int updated = jdbc.sql("""
                UPDATE enrichment_collection_attempt
                SET state = 'COMMITTED_FOR_SEND', requested_at = :at, updated_at = :at
                WHERE id = :id AND state = 'RESERVED'
                """)
                .param("id", attemptId).param("at", utc(authorizedAt)).update();
        if (updated != 1) {
            throw new IllegalStateException("Enrichment attempt authorization compare-and-set failed");
        }
        jdbc.sql("UPDATE provider_call_audit SET requested_at = :at WHERE id = (SELECT audit_id FROM enrichment_collection_attempt WHERE id = :id)")
                .param("id", attemptId).param("at", utc(authorizedAt)).update();
    }

    @Override
    public void markReleased(UUID attemptId, Instant releasedAt, String reasonCode) {
        if (!List.of("MISSED_WINDOW", "LINEUP_ALREADY_COMPLETE").contains(reasonCode)) {
            throw new IllegalArgumentException("invalid enrichment release reason");
        }
        int updated = jdbc.sql("""
                UPDATE enrichment_collection_attempt
                SET state = 'RELEASED', reason_code = :reason, updated_at = :at
                WHERE id = :id AND state = 'RESERVED'
                """)
                .param("id", attemptId).param("reason", reasonCode).param("at", utc(releasedAt)).update();
        if (updated != 1) {
            throw new IllegalStateException("Enrichment attempt release compare-and-set failed");
        }
    }

    @Override
    public void markUncertain(UUID attemptId, Instant detectedAt, String reasonCode) {
        int updated = jdbc.sql("""
                UPDATE enrichment_collection_attempt
                SET state = 'UNCERTAIN', reason_code = :reason, updated_at = :at
                WHERE id = :id AND state = 'COMMITTED_FOR_SEND'
                """)
                .param("id", attemptId).param("reason", reasonCode).param("at", utc(detectedAt)).update();
        if (updated == 0 && findAttempt(attemptId).map(item -> item.state() == EnrichmentAttemptState.UNCERTAIN).orElse(false)) {
            return;
        }
        if (updated != 1) {
            throw new IllegalStateException("Enrichment attempt uncertainty transition failed");
        }
        jdbc.sql("UPDATE provider_call_audit SET error_code = :reason WHERE id = (SELECT audit_id FROM enrichment_collection_attempt WHERE id = :id)")
                .param("id", attemptId).param("reason", reasonCode).update();
    }

    @Override
    @Transactional
    public UUID recordResponse(UUID attemptId, RawSnapshot rawSnapshot, EnrichmentProviderResponse response,
            String outcomeCode) {
        EnrichmentCollectionAttempt current = findAttempt(attemptId).orElseThrow();
        UUID rawId = null;
        boolean retainRaw = response.httpStatus() != null
                && !"SECRET_ECHO".equals(outcomeCode) && !"RESPONSE_TOO_LARGE".equals(outcomeCode);
        if (retainRaw) {
            rawId = storeRawSnapshot(current, rawSnapshot, response);
        }
        EnrichmentAttemptState next = switch (outcomeCode) {
            case "RECEIVED" -> EnrichmentAttemptState.RECEIVED;
            case "HTTP_ERROR" -> EnrichmentAttemptState.HTTP_ERROR;
            case "RESPONSE_TOO_LARGE" -> EnrichmentAttemptState.RESPONSE_TOO_LARGE;
            case "SECRET_ECHO" -> EnrichmentAttemptState.SECRET_ECHO;
            default -> EnrichmentAttemptState.RESPONSE_INTERRUPTED;
        };
        Instant completed = response.completedAt();
        int updated = jdbc.sql("""
                UPDATE enrichment_collection_attempt
                SET state = :state, raw_snapshot_id = :rawId, payload_sha256 = :payloadHash,
                    requested_at = :requested, received_at = :received, result_recorded_at = :recorded,
                    http_status = :httpStatus, quota_remaining = :quota, reason_code = :reason,
                    updated_at = :updated
                WHERE id = :id AND state = 'COMMITTED_FOR_SEND'
                """)
                .param("id", attemptId).param("state", next.name()).param("rawId", rawId)
                .param("payloadHash", rawId == null ? null : rawSnapshot.sha256())
                .param("requested", utc(response.requestedAt())).param("received", utc(completed))
                .param("recorded", utc(completed)).param("httpStatus", response.httpStatus())
                .param("quota", response.quotaRemaining())
                .param("reason", outcomeCode.equals("RECEIVED") || outcomeCode.equals("HTTP_ERROR") ? null : outcomeCode)
                .param("updated", utc(completed)).update();
        if (updated != 1) {
            throw new IllegalStateException("Enrichment response compare-and-set failed");
        }
        Long latencyMs = Math.max(0L, java.time.Duration.between(response.requestedAt(), completed).toMillis());
        jdbc.sql("""
                UPDATE provider_call_audit SET requested_at = :requested, received_at = :received,
                    http_status = :status, latency_ms = :latency, quota_remaining = :quota,
                    payload_sha256 = :hash, error_code = :error
                WHERE id = (SELECT audit_id FROM enrichment_collection_attempt WHERE id = :id)
                """)
                .param("id", attemptId).param("requested", utc(response.requestedAt()))
                .param("received", utc(completed)).param("status", response.httpStatus())
                .param("latency", latencyMs).param("quota", response.quotaRemaining())
                .param("hash", rawId == null ? null : rawSnapshot.sha256())
                .param("error", next == EnrichmentAttemptState.RECEIVED ? null : outcomeCode).update();
        return rawId;
    }

    @Override
    public void appendDerivation(EnrichmentDerivationRecord derivation) {
        jdbc.sql("""
                INSERT INTO enrichment_collection_derivation
                    (id, attempt_id, parser_version, outcome, observation_id, raw_sha256, evaluated_at)
                VALUES (:id, :attempt, :parser, :outcome, :observation, :rawHash, :at)
                """)
                .param("id", derivation.id()).param("attempt", derivation.attemptId())
                .param("parser", derivation.parserVersion()).param("outcome", derivation.outcome())
                .param("observation", derivation.observationId()).param("rawHash", derivation.rawSha256())
                .param("at", utc(derivation.evaluatedAt())).update();
    }

    private UUID storeRawSnapshot(EnrichmentCollectionAttempt attempt, RawSnapshot raw,
            EnrichmentProviderResponse response) {
        if (raw == null || !raw.provider().equals(attempt.capability().provider())
                || !raw.endpoint().equals(attempt.logicalEndpoint())
                || !raw.connectorVersion().equals(attempt.connectorVersion())) {
            throw new IllegalArgumentException("Enrichment raw snapshot provenance mismatch");
        }
        UUID candidate = UUID.randomUUID();
        long latency = Math.max(0L, java.time.Duration.between(response.requestedAt(), response.completedAt()).toMillis());
        int inserted = jdbc.sql("""
                INSERT INTO raw_snapshot (id, provider, endpoint, requested_at, received_at, http_status,
                    latency_ms, quota_remaining, payload_sha256, payload_compression, payload, connector_version)
                VALUES (:id, :provider, :endpoint, :requested, :received, :status, :latency, :quota,
                    :hash, 'identity', :payload, :connector)
                ON CONFLICT (provider, endpoint, payload_sha256) DO NOTHING
                """)
                .param("id", candidate).param("provider", raw.provider()).param("endpoint", raw.endpoint())
                .param("requested", utc(response.requestedAt())).param("received", utc(response.completedAt()))
                .param("status", response.httpStatus()).param("latency", latency)
                .param("quota", response.quotaRemaining()).param("hash", raw.sha256())
                .param("payload", raw.payload()).param("connector", raw.connectorVersion()).update();
        if (inserted == 1) {
            return candidate;
        }
        return jdbc.sql("""
                SELECT id FROM raw_snapshot WHERE provider = :provider AND endpoint = :endpoint
                    AND payload_sha256 = :hash AND payload = :payload
                """)
                .param("provider", raw.provider()).param("endpoint", raw.endpoint()).param("hash", raw.sha256())
                .param("payload", raw.payload()).query(UUID.class).optional()
                .orElseThrow(() -> new IllegalStateException("Conflicting raw snapshot evidence"));
    }

    private void insertAttempt(EnrichmentCollectionAttempt a) {
        jdbc.sql("""
                INSERT INTO enrichment_collection_attempt (id, admission_id, step_id, step_code, canonical_fixture_id,
                    budget_window_id, budget_intent_id, audit_id, provider, provider_competition_id,
                    source_season, source_phase, data_type, logical_competition, logical_season, logical_phase,
                    provider_fixture_id, family, logical_endpoint, connector_version, parser_version,
                    request_sha256, state, raw_snapshot_id, payload_sha256, kickoff_at, requested_at,
                    received_at, result_recorded_at, http_status, quota_remaining, reason_code, created_at, updated_at)
                VALUES (:id, :admission, :step, :stepCode, :fixture, :window, :intent, :audit, :provider, :competitionId,
                    :sourceSeason, :sourcePhase, :dataType, :competition, :season, :phase, :providerFixture,
                    :family, :endpoint, :connector, :parser, :requestHash, :state, NULL, NULL, :kickoff,
                    :requested, NULL, NULL, NULL, NULL, NULL, :created, :updated)
                """)
                .param("id", a.id()).param("admission", a.admissionId()).param("step", a.stepId())
                .param("stepCode", a.stepCode().name())
                .param("fixture", a.canonicalFixtureId()).param("window", a.budgetWindowId())
                .param("intent", a.budgetIntentId()).param("audit", a.auditId())
                .param("provider", a.capability().provider()).param("competitionId", a.capability().providerCompetitionId())
                .param("sourceSeason", a.capability().sourceSeason()).param("sourcePhase", a.capability().sourcePhase())
                .param("dataType", a.capability().dataType().name()).param("competition", a.logicalCompetition())
                .param("season", a.logicalSeason()).param("phase", a.logicalPhase())
                .param("providerFixture", a.providerFixtureId()).param("family", a.family().name())
                .param("endpoint", a.logicalEndpoint()).param("connector", a.connectorVersion())
                .param("parser", a.parserVersion()).param("requestHash", a.requestSha256())
                .param("state", a.state().name()).param("kickoff", utc(a.kickoffAt()))
                .param("requested", utc(a.requestedAt())).param("created", utc(a.createdAt()))
                .param("updated", utc(a.updatedAt())).update();
    }

    private EnrichmentCollectionAttempt mapAttempt(ResultSet rs, int row) throws SQLException {
        return new EnrichmentCollectionAttempt(rs.getObject("id", UUID.class), rs.getObject("admission_id", UUID.class),
                rs.getObject("step_id", UUID.class), EnrichmentPlanStepCode.valueOf(rs.getString("step_code")),
                rs.getObject("canonical_fixture_id", UUID.class), rs.getObject("budget_window_id", UUID.class),
                rs.getObject("budget_intent_id", UUID.class), rs.getObject("audit_id", UUID.class),
                new ProviderCapabilityKey(rs.getString("provider"), rs.getString("provider_competition_id"),
                        rs.getString("source_season"), rs.getString("source_phase"),
                        CapabilityDataType.valueOf(rs.getString("data_type"))),
                rs.getString("logical_competition"), rs.getString("logical_season"), rs.getString("logical_phase"),
                rs.getString("provider_fixture_id"), EnrichmentFamily.valueOf(rs.getString("family")),
                rs.getString("logical_endpoint"), rs.getString("connector_version"), rs.getString("parser_version"),
                rs.getString("request_sha256"), EnrichmentAttemptState.valueOf(rs.getString("state")),
                rs.getObject("raw_snapshot_id", UUID.class), rs.getString("payload_sha256"), instant(rs, "kickoff_at"),
                instant(rs, "requested_at"), instant(rs, "received_at"), instant(rs, "result_recorded_at"),
                (Integer) rs.getObject("http_status"), (Long) rs.getObject("quota_remaining"), rs.getString("reason_code"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static boolean sameRequest(EnrichmentCollectionAttempt left, EnrichmentCollectionAttempt right) {
        return left.admissionId().equals(right.admissionId()) && left.stepId().equals(right.stepId())
                && left.canonicalFixtureId().equals(right.canonicalFixtureId())
                && left.budgetWindowId().equals(right.budgetWindowId()) && left.capability().equals(right.capability())
                && left.logicalCompetition().equals(right.logicalCompetition())
                && left.logicalSeason().equals(right.logicalSeason()) && left.logicalPhase().equals(right.logicalPhase())
                && left.providerFixtureId().equals(right.providerFixtureId()) && left.family() == right.family()
                && left.logicalEndpoint().equals(right.logicalEndpoint())
                && left.connectorVersion().equals(right.connectorVersion())
                && left.parserVersion().equals(right.parserVersion()) && left.requestSha256().equals(right.requestSha256());
    }

    private static OffsetDateTime utc(Instant value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
