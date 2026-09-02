package com.bettingproject.identity.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.bettingproject.identity.application.NormalizationAnomalyRepository;
import com.bettingproject.identity.application.StoredNormalizationAnomaly;
import com.bettingproject.identity.domain.NormalizationAnomaly;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyCode;
import com.bettingproject.identity.domain.ProviderEntityType;
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
    public StoredNormalizationAnomaly insertOrResolveForUpdate(NormalizationAnomaly anomaly) {
        List<NormalizationAnomaly> related = findRelatedForUpdate(anomaly);
        for (NormalizationAnomaly existing : related) {
            if (sameContext(existing, anomaly)) {
                return new StoredNormalizationAnomaly(existing, false);
            }
        }
        if (hasContext(anomaly) && related.size() == 1 && hasNoContext(related.getFirst())) {
            NormalizationAnomaly historical = related.getFirst();
            int enriched = jdbcClient.sql("""
                    UPDATE normalization_anomaly
                    SET season = :season,
                        phase = :phase
                    WHERE id = :id
                      AND season IS NULL
                      AND phase IS NULL
                    """)
                    .param("season", anomaly.season())
                    .param("phase", anomaly.phase())
                    .param("id", historical.id())
                    .update();
            if (enriched != 1) {
                throw new IllegalStateException("Historical anomaly context changed while locked");
            }
            return new StoredNormalizationAnomaly(withContext(historical, anomaly), false);
        }

        int inserted = jdbcClient.sql("""
                INSERT INTO normalization_anomaly (
                    id, raw_snapshot_id, provider, entity_type, provider_entity_id, season, phase,
                    anomaly_code, details, status, version, created_at, last_seen_at,
                    updated_at, resolved_at, occurrence_count
                ) VALUES (
                    :id, :rawSnapshotId, :provider, :entityType, :providerEntityId, :season, :phase,
                    :anomalyCode, :details, :status, :version, :createdAt, :lastSeenAt,
                    :updatedAt, :resolvedAt, :occurrenceCount
                )
                ON CONFLICT (
                    raw_snapshot_id, provider, entity_type, provider_entity_id,
                    season, phase, anomaly_code
                ) DO NOTHING
                """)
                .params(parameters(anomaly))
                .update();
        if (inserted == 1) {
            return new StoredNormalizationAnomaly(anomaly, true);
        }
        NormalizationAnomaly existing = jdbcClient.sql("""
                SELECT id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                       season, phase, anomaly_code, details, status, version, created_at,
                       last_seen_at, updated_at, resolved_at, occurrence_count
                FROM normalization_anomaly
                WHERE raw_snapshot_id = :rawSnapshotId
                  AND provider = :provider
                  AND entity_type = :entityType
                  AND provider_entity_id = :providerEntityId
                  AND season IS NOT DISTINCT FROM :season
                  AND phase IS NOT DISTINCT FROM :phase
                  AND anomaly_code = :anomalyCode
                FOR UPDATE
                """)
                .param("rawSnapshotId", anomaly.rawSnapshotId())
                .param("provider", anomaly.provider())
                .param("entityType", anomaly.entityType().name())
                .param("providerEntityId", anomaly.providerEntityId())
                .param("season", anomaly.season())
                .param("phase", anomaly.phase())
                .param("anomalyCode", anomaly.code().name())
                .query(this::map)
                .single();
        return new StoredNormalizationAnomaly(existing, false);
    }

    private List<NormalizationAnomaly> findRelatedForUpdate(NormalizationAnomaly anomaly) {
        return jdbcClient.sql("""
                SELECT id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                       season, phase, anomaly_code, details, status, version, created_at,
                       last_seen_at, updated_at, resolved_at, occurrence_count
                FROM normalization_anomaly
                WHERE raw_snapshot_id = :rawSnapshotId
                  AND provider = :provider
                  AND entity_type = :entityType
                  AND provider_entity_id = :providerEntityId
                  AND anomaly_code = :anomalyCode
                ORDER BY id
                FOR UPDATE
                """)
                .param("rawSnapshotId", anomaly.rawSnapshotId())
                .param("provider", anomaly.provider())
                .param("entityType", anomaly.entityType().name())
                .param("providerEntityId", anomaly.providerEntityId())
                .param("anomalyCode", anomaly.code().name())
                .query(this::map)
                .list();
    }

    private boolean sameContext(NormalizationAnomaly left, NormalizationAnomaly right) {
        return Objects.equals(left.season(), right.season())
                && Objects.equals(left.phase(), right.phase());
    }

    private boolean hasContext(NormalizationAnomaly anomaly) {
        return anomaly.season() != null || anomaly.phase() != null;
    }

    private boolean hasNoContext(NormalizationAnomaly anomaly) {
        return anomaly.season() == null && anomaly.phase() == null;
    }

    private NormalizationAnomaly withContext(
            NormalizationAnomaly historical,
            NormalizationAnomaly contextualOccurrence) {
        return new NormalizationAnomaly(
                historical.id(),
                historical.rawSnapshotId(),
                historical.provider(),
                historical.entityType(),
                historical.providerEntityId(),
                contextualOccurrence.season(),
                contextualOccurrence.phase(),
                historical.code(),
                historical.details(),
                historical.status(),
                historical.version(),
                historical.createdAt(),
                historical.lastSeenAt(),
                historical.updatedAt(),
                historical.resolvedAt(),
                historical.occurrenceCount());
    }

    @Override
    public boolean updateIfVersion(NormalizationAnomaly anomaly, long expectedVersion) {
        int updated = jdbcClient.sql("""
                UPDATE normalization_anomaly
                SET details = :details,
                    status = :status,
                    version = :version,
                    last_seen_at = :lastSeenAt,
                    updated_at = :updatedAt,
                    resolved_at = :resolvedAt,
                    occurrence_count = :occurrenceCount
                WHERE id = :id
                  AND version = :expectedVersion
                """)
                .params(parameters(anomaly))
                .param("expectedVersion", expectedVersion)
                .update();
        return updated == 1;
    }

    @Override
    public List<NormalizationAnomaly> findOpenBySnapshotForUpdate(UUID rawSnapshotId) {
        return jdbcClient.sql("""
                SELECT id, raw_snapshot_id, provider, entity_type, provider_entity_id,
                       season, phase, anomaly_code, details, status, version, created_at,
                       last_seen_at, updated_at, resolved_at, occurrence_count
                FROM normalization_anomaly
                WHERE raw_snapshot_id = :rawSnapshotId
                  AND status = 'OPEN'
                ORDER BY id
                FOR UPDATE
                """)
                .param("rawSnapshotId", rawSnapshotId)
                .query(this::map)
                .list();
    }

    private java.util.Map<String, Object> parameters(NormalizationAnomaly anomaly) {
        java.util.Map<String, Object> parameters = new java.util.HashMap<>();
        parameters.put("id", anomaly.id());
        parameters.put("rawSnapshotId", anomaly.rawSnapshotId());
        parameters.put("provider", anomaly.provider());
        parameters.put("entityType", anomaly.entityType().name());
        parameters.put("providerEntityId", anomaly.providerEntityId());
        parameters.put("season", anomaly.season());
        parameters.put("phase", anomaly.phase());
        parameters.put("anomalyCode", anomaly.code().name());
        parameters.put("details", anomaly.details());
        parameters.put("status", anomaly.status().name());
        parameters.put("version", anomaly.version());
        parameters.put("createdAt", utc(anomaly.createdAt()));
        parameters.put("lastSeenAt", utc(anomaly.lastSeenAt()));
        parameters.put("updatedAt", utc(anomaly.updatedAt()));
        parameters.put("resolvedAt", anomaly.resolvedAt() == null ? null : utc(anomaly.resolvedAt()));
        parameters.put("occurrenceCount", anomaly.occurrenceCount());
        return parameters;
    }

    private NormalizationAnomaly map(ResultSet resultSet, int rowNumber) throws SQLException {
        OffsetDateTime resolvedAt = resultSet.getObject("resolved_at", OffsetDateTime.class);
        return new NormalizationAnomaly(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("raw_snapshot_id", UUID.class),
                resultSet.getString("provider"),
                ProviderEntityType.valueOf(resultSet.getString("entity_type")),
                resultSet.getString("provider_entity_id"),
                resultSet.getString("season"),
                resultSet.getString("phase"),
                NormalizationAnomalyCode.valueOf(resultSet.getString("anomaly_code")),
                resultSet.getString("details"),
                AnomalyStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("last_seen_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant(),
                resolvedAt == null ? null : resolvedAt.toInstant(),
                resultSet.getLong("occurrence_count"));
    }

    private OffsetDateTime utc(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
