package com.bettingproject.catalog.adapter.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.catalog.application.NormalizationReplayApplicationStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcNormalizationReplayApplicationStore implements NormalizationReplayApplicationStore {

    private final JdbcClient jdbcClient;

    public JdbcNormalizationReplayApplicationStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Set<UUID> findApplicationIdsBySnapshotId(UUID snapshotId) {
        return new LinkedHashSet<>(jdbcClient.sql("""
                SELECT application.id
                FROM fixture_application_log AS application
                JOIN fixture_observation AS observation
                  ON observation.id = application.fixture_observation_id
                WHERE observation.raw_snapshot_id = :snapshotId
                ORDER BY application.evaluated_at, application.id
                """)
                .param("snapshotId", snapshotId)
                .query(UUID.class)
                .list());
    }

    @Override
    public void correlate(
            UUID attemptId,
            Collection<UUID> fixtureApplicationLogIds,
            Instant createdAt) {
        for (UUID applicationLogId : fixtureApplicationLogIds) {
            jdbcClient.sql("""
                    INSERT INTO normalization_replay_attempt_application (
                        normalization_replay_attempt_id,
                        fixture_application_log_id,
                        created_at
                    ) VALUES (
                        :attemptId,
                        :applicationLogId,
                        :createdAt
                    )
                    """)
                    .param("attemptId", attemptId)
                    .param("applicationLogId", applicationLogId)
                    .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
                    .update();
        }
    }
}
