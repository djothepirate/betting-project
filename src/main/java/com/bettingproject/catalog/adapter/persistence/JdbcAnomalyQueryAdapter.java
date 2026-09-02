package com.bettingproject.catalog.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.AnomalyQueryPort;
import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyEventView;
import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyQueryFilter;
import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyView;
import com.bettingproject.catalog.application.TimestampKeysetAnchor;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyCode;
import com.bettingproject.identity.domain.NormalizationAnomalyEventType;
import com.bettingproject.identity.domain.ProviderEntityType;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcAnomalyQueryAdapter implements AnomalyQueryPort {

    private static final String ANOMALY_SELECT = """
            SELECT a.id, a.raw_snapshot_id, a.provider, a.entity_type,
                   a.provider_entity_id, a.season, a.phase, a.anomaly_code,
                   a.details, a.status, a.version, a.created_at, a.last_seen_at,
                   a.updated_at, a.resolved_at, a.occurrence_count,
                   s.id AS snapshot_id, s.provider AS snapshot_provider,
                   s.endpoint AS snapshot_endpoint,
                   s.requested_at AS snapshot_requested_at,
                   s.received_at AS snapshot_received_at,
                   s.source_observed_at AS snapshot_source_observed_at,
                   s.http_status AS snapshot_http_status,
                   s.latency_ms AS snapshot_latency_ms,
                   s.quota_remaining AS snapshot_quota_remaining,
                   s.payload_sha256 AS snapshot_payload_sha256,
                   s.payload_compression AS snapshot_payload_compression,
                   s.connector_version AS snapshot_connector_version,
                   s.created_at AS snapshot_created_at
            FROM normalization_anomaly a
            JOIN raw_snapshot s ON s.id = a.raw_snapshot_id
            """;

    private final JdbcClient jdbcClient;

    public JdbcAnomalyQueryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<AnomalyView> fetchAnomalies(
            AnomalyQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int fetchLimit) {
        return jdbcClient.sql(ANOMALY_SELECT + """
                WHERE a.status = :status
                  AND (CAST(:provider AS VARCHAR) IS NULL OR a.provider = :provider)
                  AND (CAST(:code AS VARCHAR) IS NULL OR a.anomaly_code = :code)
                  AND (CAST(:entityType AS VARCHAR) IS NULL OR a.entity_type = :entityType)
                  AND (CAST(:snapshotId AS UUID) IS NULL OR a.raw_snapshot_id = :snapshotId)
                  AND (CAST(:providerEntityId AS VARCHAR) IS NULL
                       OR a.provider_entity_id = :providerEntityId)
                  AND (
                      CAST(:anchorTimestamp AS TIMESTAMPTZ) IS NULL
                      OR (a.created_at, a.id) < (:anchorTimestamp, :anchorId)
                  )
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT :fetchLimit
                """)
                .param("status", filter.status().name())
                .param("provider", filter.provider())
                .param("code", nameOrNull(filter.code()))
                .param("entityType", nameOrNull(filter.entityType()))
                .param("snapshotId", filter.rawSnapshotId())
                .param("providerEntityId", filter.providerEntityId())
                .param("anchorTimestamp", anchor == null ? null : anchor.timestamp().atOffset(ZoneOffset.UTC))
                .param("anchorId", anchor == null ? null : anchor.id())
                .param("fetchLimit", fetchLimit)
                .query(this::mapAnomaly)
                .list();
    }

    @Override
    public Optional<AnomalyView> findAnomaly(UUID anomalyId) {
        return jdbcClient.sql(ANOMALY_SELECT + " WHERE a.id = :anomalyId")
                .param("anomalyId", anomalyId)
                .query(this::mapAnomaly)
                .optional();
    }

    @Override
    public List<AnomalyEventView> fetchEvents(
            UUID anomalyId,
            TimestampKeysetAnchor anchor,
            int fetchLimit) {
        return jdbcClient.sql("""
                SELECT e.id, e.normalization_anomaly_id, e.event_type,
                       e.previous_status, e.resulting_status,
                       e.fixture_application_log_id, e.details, e.created_at
                FROM normalization_anomaly_event e
                WHERE e.normalization_anomaly_id = :anomalyId
                  AND (
                      CAST(:anchorTimestamp AS TIMESTAMPTZ) IS NULL
                      OR (e.created_at, e.id) < (:anchorTimestamp, :anchorId)
                  )
                ORDER BY e.created_at DESC, e.id DESC
                LIMIT :fetchLimit
                """)
                .param("anomalyId", anomalyId)
                .param("anchorTimestamp", anchor == null ? null : anchor.timestamp().atOffset(ZoneOffset.UTC))
                .param("anchorId", anchor == null ? null : anchor.id())
                .param("fetchLimit", fetchLimit)
                .query(this::mapEvent)
                .list();
    }

    private AnomalyView mapAnomaly(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AnomalyView(
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
                JdbcCatalogQueryMappings.instant(resultSet, "created_at"),
                JdbcCatalogQueryMappings.instant(resultSet, "last_seen_at"),
                JdbcCatalogQueryMappings.instant(resultSet, "updated_at"),
                JdbcCatalogQueryMappings.instantOrNull(resultSet, "resolved_at"),
                resultSet.getLong("occurrence_count"),
                JdbcCatalogQueryMappings.snapshot(resultSet));
    }

    private AnomalyEventView mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        String previousStatus = resultSet.getString("previous_status");
        return new AnomalyEventView(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("normalization_anomaly_id", UUID.class),
                NormalizationAnomalyEventType.valueOf(resultSet.getString("event_type")),
                previousStatus == null ? null : AnomalyStatus.valueOf(previousStatus),
                AnomalyStatus.valueOf(resultSet.getString("resulting_status")),
                resultSet.getObject("fixture_application_log_id", UUID.class),
                resultSet.getString("details"),
                JdbcCatalogQueryMappings.instant(resultSet, "created_at"));
    }

    private static String nameOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
