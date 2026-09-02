package com.bettingproject.catalog.adapter.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.catalog.application.NormalizationReplayAnomalyEventStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcNormalizationReplayAnomalyEventStore implements NormalizationReplayAnomalyEventStore {

    private final JdbcClient jdbcClient;

    public JdbcNormalizationReplayAnomalyEventStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Set<UUID> findAnomalyEventIdsBySnapshotId(UUID snapshotId) {
        return new LinkedHashSet<>(jdbcClient.sql("""
                SELECT event.id
                FROM normalization_anomaly_event AS event
                JOIN normalization_anomaly AS anomaly
                  ON anomaly.id = event.normalization_anomaly_id
                WHERE anomaly.raw_snapshot_id = :snapshotId
                ORDER BY event.created_at, event.id
                """)
                .param("snapshotId", snapshotId)
                .query(UUID.class)
                .list());
    }

    @Override
    public void correlate(UUID attemptId, Collection<UUID> anomalyEventIds, Instant createdAt) {
        for (UUID anomalyEventId : anomalyEventIds) {
            jdbcClient.sql("""
                    INSERT INTO normalization_replay_attempt_anomaly_event (
                        normalization_replay_attempt_id,
                        normalization_anomaly_event_id,
                        created_at
                    ) VALUES (
                        :attemptId,
                        :anomalyEventId,
                        :createdAt
                    )
                    """)
                    .param("attemptId", attemptId)
                    .param("anomalyEventId", anomalyEventId)
                    .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
                    .update();
        }
    }
}
