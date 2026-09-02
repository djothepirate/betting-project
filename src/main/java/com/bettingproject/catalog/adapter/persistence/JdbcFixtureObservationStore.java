package com.bettingproject.catalog.adapter.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.bettingproject.catalog.application.FixtureObservationStore;
import com.bettingproject.catalog.application.StoredFixtureObservation;
import com.bettingproject.catalog.domain.FixtureObservation;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcFixtureObservationStore implements FixtureObservationStore {

    private final JdbcClient jdbcClient;

    public JdbcFixtureObservationStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public StoredFixtureObservation storeAndResolve(FixtureObservation observation) {
        int inserted = jdbcClient.sql("""
                INSERT INTO fixture_observation (
                    id, raw_snapshot_id, canonical_fixture_id, provider, provider_fixture_id,
                    provider_competition_id, provider_home_team_id, provider_away_team_id,
                    source_schema_version, source_season, source_neutral_venue,
                    source_participants_unordered, source_kickoff_at, source_status, source_phase,
                    normalization_status, reason_code, observed_at, created_at
                ) VALUES (
                    :id, :rawSnapshotId, :canonicalFixtureId, :provider, :providerFixtureId,
                    :providerCompetitionId, :providerHomeTeamId, :providerAwayTeamId,
                    :sourceSchemaVersion, :sourceSeason, :sourceNeutralVenue,
                    :sourceParticipantsUnordered, :sourceKickoff, :sourceStatus, :sourcePhase,
                    :normalizationStatus, :reasonCode, :observedAt, :createdAt
                )
                ON CONFLICT (raw_snapshot_id, provider_fixture_id) DO NOTHING
                """)
                .param("id", observation.id())
                .param("rawSnapshotId", observation.rawSnapshotId())
                .param("canonicalFixtureId", observation.canonicalFixtureId())
                .param("provider", observation.provider())
                .param("providerFixtureId", observation.providerFixtureId())
                .param("providerCompetitionId", observation.providerCompetitionId())
                .param("providerHomeTeamId", observation.providerHomeTeamId())
                .param("providerAwayTeamId", observation.providerAwayTeamId())
                .param("sourceSchemaVersion", observation.sourceSchemaVersion())
                .param("sourceSeason", observation.sourceSeason())
                .param("sourceNeutralVenue", observation.sourceNeutralVenue())
                .param("sourceParticipantsUnordered", observation.sourceParticipantsUnordered())
                .param("sourceKickoff", utc(observation.sourceKickoff()))
                .param("sourceStatus", observation.sourceStatus())
                .param("sourcePhase", observation.sourcePhase())
                .param("normalizationStatus", observation.normalizationStatus().name())
                .param("reasonCode", observation.reasonCode())
                .param("observedAt", utc(observation.observedAt()))
                .param("createdAt", utc(observation.createdAt()))
                .update();
        if (inserted == 1) {
            return new StoredFixtureObservation(observation.id(), true);
        }
        UUID existingId = jdbcClient.sql("""
                SELECT id
                FROM fixture_observation
                WHERE raw_snapshot_id = :rawSnapshotId
                  AND provider_fixture_id = :providerFixtureId
                """)
                .param("rawSnapshotId", observation.rawSnapshotId())
                .param("providerFixtureId", observation.providerFixtureId())
                .query(UUID.class)
                .single();
        return new StoredFixtureObservation(existingId, false);
    }

    private OffsetDateTime utc(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
