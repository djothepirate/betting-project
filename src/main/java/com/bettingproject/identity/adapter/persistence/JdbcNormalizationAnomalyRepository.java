package com.bettingproject.identity.adapter.persistence;

import java.time.ZoneOffset;

import com.bettingproject.identity.application.NormalizationAnomalyRepository;
import com.bettingproject.identity.domain.NormalizationAnomaly;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcNormalizationAnomalyRepository implements NormalizationAnomalyRepository {

    private final JdbcClient jdbcClient;

    public JdbcNormalizationAnomalyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean save(NormalizationAnomaly anomaly) {
        int inserted = jdbcClient.sql("""
                INSERT INTO normalization_anomaly (
                    id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                    anomaly_code, details, status, created_at, resolved_at
                ) VALUES (
                    :id, :rawSnapshotId, :provider, :entityType, :providerEntityId,
                    :anomalyCode, :details, :status, :createdAt, :resolvedAt
                )
                ON CONFLICT (raw_snapshot_id, entity_type, provider_entity_id, anomaly_code) DO NOTHING
                """)
                .param("id", anomaly.id())
                .param("rawSnapshotId", anomaly.rawSnapshotId())
                .param("provider", anomaly.provider())
                .param("entityType", anomaly.entityType().name())
                .param("providerEntityId", anomaly.providerEntityId())
                .param("anomalyCode", anomaly.code().name())
                .param("details", anomaly.details())
                .param("status", anomaly.status().name())
                .param("createdAt", anomaly.createdAt().atOffset(ZoneOffset.UTC))
                .param("resolvedAt", anomaly.resolvedAt() == null
                        ? null
                        : anomaly.resolvedAt().atOffset(ZoneOffset.UTC))
                .update();
        return inserted == 1;
    }
}
