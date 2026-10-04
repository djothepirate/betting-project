package com.bettingproject.collection.adapter.persistence;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import com.bettingproject.collection.application.enrichment.EnrichmentObservationStore;
import com.bettingproject.collection.application.enrichment.EnrichmentObservationWrite;
import com.bettingproject.collection.application.enrichment.StoredEnrichmentObservation;
import com.bettingproject.enrichment.domain.ProviderEnrichmentObservation;
import com.bettingproject.qualification.domain.QualityFinding;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Append-only persistence for derived JSON and findings; raw response bytes are never copied. */
@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcEnrichmentObservationStore implements EnrichmentObservationStore {
    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcEnrichmentObservationStore(JdbcClient jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Override @Transactional
    public StoredEnrichmentObservation storeAndResolve(EnrichmentObservationWrite write) {
        ProviderEnrichmentObservation observation = write.observation();
        UUID candidateId = observation.id();
        int inserted = jdbc.sql("""
                INSERT INTO provider_enrichment_observation (id, admission_id, canonical_fixture_id, budget_intent_id,
                    raw_snapshot_id, provider, provider_fixture_id, logical_competition, logical_season, logical_phase,
                    source_season_reference, source_phase_reference, family, observation_state, payload_sha256,
                    representation_sha256, representation_json, parser_version, requested_at, received_at,
                    source_observed_at, created_at)
                VALUES (:id, :admission, :fixture, :intent, :snapshot, :provider, :providerFixture, :competition,
                    :season, :phase, :sourceSeason, :sourcePhase, :family, :state, :payloadHash,
                    :representationHash, CAST(:representation AS jsonb), :parser, :requested, :received,
                    :sourceObserved, :created)
                ON CONFLICT (budget_intent_id, provider_fixture_id, family, parser_version) DO NOTHING
                """)
                .param("id", candidateId).param("admission", observation.admissionId())
                .param("fixture", observation.canonicalFixtureId()).param("intent", observation.budgetIntentId())
                .param("snapshot", observation.rawSnapshotId()).param("provider", observation.provider())
                .param("providerFixture", observation.providerFixtureId()).param("competition", observation.logicalCompetition())
                .param("season", observation.logicalSeason()).param("phase", observation.logicalPhase())
                .param("sourceSeason", observation.sourceSeasonReference()).param("sourcePhase", observation.sourcePhaseReference())
                .param("family", observation.family().name()).param("state", observation.state().name())
                .param("payloadHash", observation.payloadSha256()).param("representationHash", write.representationSha256())
                .param("representation", write.representationJson()).param("parser", observation.parserVersion())
                .param("requested", observation.requestedAt().atOffset(ZoneOffset.UTC))
                .param("received", observation.receivedAt().atOffset(ZoneOffset.UTC))
                .param("sourceObserved", observation.sourceObservedAt() == null ? null : observation.sourceObservedAt().atOffset(ZoneOffset.UTC))
                .param("created", clock.instant().atOffset(ZoneOffset.UTC)).update();
        UUID storedId = inserted == 1 ? candidateId : jdbc.sql("""
                SELECT id FROM provider_enrichment_observation
                WHERE budget_intent_id = :intent AND provider_fixture_id = :providerFixture
                    AND family = :family AND parser_version = :parser
                    AND admission_id = :admission AND canonical_fixture_id = :fixture
                    AND raw_snapshot_id = :snapshot AND provider = :provider
                    AND logical_competition = :competition AND logical_season = :season
                    AND logical_phase = :phase
                    AND source_season_reference IS NOT DISTINCT FROM :sourceSeason
                    AND source_phase_reference IS NOT DISTINCT FROM :sourcePhase
                    AND observation_state = :state AND payload_sha256 = :payloadHash
                    AND representation_sha256 = :representationHash
                    AND requested_at = :requested AND received_at = :received
                    AND source_observed_at IS NOT DISTINCT FROM :sourceObserved
                """)
                .param("intent", observation.budgetIntentId()).param("providerFixture", observation.providerFixtureId())
                .param("family", observation.family().name()).param("parser", observation.parserVersion())
                .param("admission", observation.admissionId()).param("fixture", observation.canonicalFixtureId())
                .param("snapshot", observation.rawSnapshotId()).param("provider", observation.provider())
                .param("competition", observation.logicalCompetition()).param("season", observation.logicalSeason())
                .param("phase", observation.logicalPhase()).param("sourceSeason", observation.sourceSeasonReference())
                .param("sourcePhase", observation.sourcePhaseReference()).param("state", observation.state().name())
                .param("payloadHash", observation.payloadSha256()).param("representationHash", write.representationSha256())
                .param("requested", observation.requestedAt().atOffset(ZoneOffset.UTC))
                .param("received", observation.receivedAt().atOffset(ZoneOffset.UTC))
                .param("sourceObserved", observation.sourceObservedAt() == null ? null : observation.sourceObservedAt().atOffset(ZoneOffset.UTC))
                .query(UUID.class).optional()
                .orElseThrow(() -> new IllegalStateException("Conflicting replay for an immutable provider observation"));
        int findingsInserted = 0;
        for (QualityFinding finding : write.findings()) {
            findingsInserted += jdbc.sql("""
                    INSERT INTO enrichment_quality_finding (id, enrichment_observation_id, issue_code,
                        entity_scope, provider_entity_id, detected_at)
                    VALUES (:id, :observation, :code, :scope, :entity, :detected)
                    ON CONFLICT (enrichment_observation_id, issue_code, entity_scope, provider_entity_id) DO NOTHING
                    """)
                    .param("id", UUID.randomUUID()).param("observation", storedId).param("code", finding.code().name())
                    .param("scope", finding.entityScope().name()).param("entity", finding.providerEntityId())
                    .param("detected", finding.detectedAt().atOffset(ZoneOffset.UTC)).update();
        }
        return new StoredEnrichmentObservation(storedId, inserted == 1, findingsInserted);
    }
}
