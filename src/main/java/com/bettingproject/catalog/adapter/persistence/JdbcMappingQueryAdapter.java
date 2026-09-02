package com.bettingproject.catalog.adapter.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyView;
import com.bettingproject.catalog.application.CorrelationKeysetAnchor;
import com.bettingproject.catalog.application.MappingQueryPort;
import com.bettingproject.catalog.application.MappingQueryPort.DecisionAnomalyView;
import com.bettingproject.catalog.application.MappingQueryPort.MappingDecisionQueryFilter;
import com.bettingproject.catalog.application.MappingQueryPort.MappingDecisionView;
import com.bettingproject.catalog.application.MappingQueryPort.MappingQueryFilter;
import com.bettingproject.catalog.application.MappingQueryPort.MappingView;
import com.bettingproject.catalog.application.TimestampKeysetAnchor;
import com.bettingproject.identity.domain.MappingDecisionType;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyCode;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcMappingQueryAdapter implements MappingQueryPort {

    private static final String MAPPING_SELECT = """
            SELECT m.id, m.provider, m.entity_type, m.provider_entity_id,
                   m.canonical_entity_id, m.season, m.phase, m.confidence,
                   m.mapping_status, m.created_at, m.updated_at, m.version
            FROM provider_mapping m
            """;

    private static final String DECISION_SELECT = """
            SELECT d.id AS decision_id, d.provider_mapping_id,
                   d.decision_type, d.expected_version, d.resulting_version,
                   d.previous_mapping_status, d.previous_canonical_entity_id,
                   d.previous_confidence, d.resulting_mapping_status,
                   d.resulting_canonical_entity_id, d.resulting_confidence,
                   d.operator_id, d.justification, d.created_at AS decision_created_at,
                   m.provider, m.entity_type, m.provider_entity_id, m.season, m.phase
            FROM provider_mapping_decision d
            JOIN provider_mapping m ON m.id = d.provider_mapping_id
            """;

    private final JdbcClient jdbcClient;

    public JdbcMappingQueryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<MappingView> fetchMappings(
            MappingQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int fetchLimit) {
        return jdbcClient.sql(MAPPING_SELECT + """
                WHERE (CAST(:status AS VARCHAR) IS NULL OR m.mapping_status = :status)
                  AND (CAST(:provider AS VARCHAR) IS NULL OR m.provider = :provider)
                  AND (CAST(:entityType AS VARCHAR) IS NULL OR m.entity_type = :entityType)
                  AND (CAST(:providerEntityId AS VARCHAR) IS NULL
                       OR m.provider_entity_id = :providerEntityId)
                  AND (:seasonFiltered = FALSE OR m.season = :season)
                  AND (:phaseFiltered = FALSE OR m.phase = :phase)
                  AND (CAST(:canonicalEntityId AS UUID) IS NULL
                       OR m.canonical_entity_id = :canonicalEntityId)
                  AND (
                      CAST(:anchorTimestamp AS TIMESTAMPTZ) IS NULL
                      OR (m.updated_at, m.id) < (:anchorTimestamp, :anchorId)
                  )
                ORDER BY m.updated_at DESC, m.id DESC
                LIMIT :fetchLimit
                """)
                .param("status", nameOrNull(filter.status()))
                .param("provider", filter.provider())
                .param("entityType", nameOrNull(filter.entityType()))
                .param("providerEntityId", filter.providerEntityId())
                .param("seasonFiltered", filter.season() != null)
                .param("season", filter.season())
                .param("phaseFiltered", filter.phase() != null)
                .param("phase", filter.phase())
                .param("canonicalEntityId", filter.canonicalEntityId())
                .param("anchorTimestamp", anchor == null ? null : anchor.timestamp().atOffset(ZoneOffset.UTC))
                .param("anchorId", anchor == null ? null : anchor.id())
                .param("fetchLimit", fetchLimit)
                .query(this::mapMapping)
                .list();
    }

    @Override
    public Optional<MappingView> findMapping(UUID mappingId) {
        return jdbcClient.sql(MAPPING_SELECT + " WHERE m.id = :mappingId")
                .param("mappingId", mappingId)
                .query(this::mapMapping)
                .optional();
    }

    @Override
    public List<MappingDecisionView> fetchDecisions(
            MappingDecisionQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int fetchLimit) {
        return jdbcClient.sql(DECISION_SELECT + """
                WHERE (CAST(:mappingId AS UUID) IS NULL OR d.provider_mapping_id = :mappingId)
                  AND (
                      CAST(:anchorTimestamp AS TIMESTAMPTZ) IS NULL
                      OR (d.created_at, d.id) < (:anchorTimestamp, :anchorId)
                  )
                ORDER BY d.created_at DESC, d.id DESC
                LIMIT :fetchLimit
                """)
                .param("mappingId", filter.mappingId())
                .param("anchorTimestamp", anchor == null ? null : anchor.timestamp().atOffset(ZoneOffset.UTC))
                .param("anchorId", anchor == null ? null : anchor.id())
                .param("fetchLimit", fetchLimit)
                .query(this::mapDecision)
                .list();
    }

    @Override
    public Optional<MappingDecisionView> findDecision(UUID decisionId) {
        return jdbcClient.sql(DECISION_SELECT + " WHERE d.id = :decisionId")
                .param("decisionId", decisionId)
                .query(this::mapDecision)
                .optional();
    }

    @Override
    public List<DecisionAnomalyView> fetchCorrelatedAnomalies(
            UUID decisionId,
            CorrelationKeysetAnchor anchor,
            int fetchLimit) {
        return jdbcClient.sql("""
                SELECT a.id, a.raw_snapshot_id, a.provider, a.entity_type,
                       a.provider_entity_id, a.season, a.phase, a.anomaly_code,
                       a.details, a.status, a.version, a.created_at, a.last_seen_at,
                       a.updated_at, a.resolved_at, a.occurrence_count,
                       c.created_at AS correlation_created_at,
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
                FROM provider_mapping_decision_anomaly c
                JOIN normalization_anomaly a ON a.id = c.normalization_anomaly_id
                JOIN raw_snapshot s ON s.id = a.raw_snapshot_id
                WHERE c.provider_mapping_decision_id = :decisionId
                  AND (
                      CAST(:anchorTimestamp AS TIMESTAMPTZ) IS NULL
                      OR (c.created_at, a.id) < (:anchorTimestamp, :anchorId)
                  )
                ORDER BY c.created_at DESC, a.id DESC
                LIMIT :fetchLimit
                """)
                .param("decisionId", decisionId)
                .param("anchorTimestamp", anchor == null ? null : anchor.createdAt().atOffset(ZoneOffset.UTC))
                .param("anchorId", anchor == null ? null : anchor.targetId())
                .param("fetchLimit", fetchLimit)
                .query(this::mapDecisionAnomaly)
                .list();
    }

    private MappingView mapMapping(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MappingView(
                resultSet.getObject("id", UUID.class),
                mappingKey(resultSet),
                resultSet.getObject("canonical_entity_id", UUID.class),
                doubleOrNull(resultSet, "confidence"),
                MappingStatus.valueOf(resultSet.getString("mapping_status")),
                resultSet.getLong("version"),
                JdbcCatalogQueryMappings.instant(resultSet, "created_at"),
                JdbcCatalogQueryMappings.instant(resultSet, "updated_at"));
    }

    private MappingDecisionView mapDecision(ResultSet resultSet, int rowNumber) throws SQLException {
        String previousStatus = resultSet.getString("previous_mapping_status");
        return new MappingDecisionView(
                resultSet.getObject("decision_id", UUID.class),
                resultSet.getObject("provider_mapping_id", UUID.class),
                mappingKey(resultSet),
                MappingDecisionType.valueOf(resultSet.getString("decision_type")),
                resultSet.getLong("expected_version"),
                resultSet.getLong("resulting_version"),
                previousStatus == null ? null : MappingStatus.valueOf(previousStatus),
                resultSet.getObject("previous_canonical_entity_id", UUID.class),
                doubleOrNull(resultSet, "previous_confidence"),
                MappingStatus.valueOf(resultSet.getString("resulting_mapping_status")),
                resultSet.getObject("resulting_canonical_entity_id", UUID.class),
                doubleOrNull(resultSet, "resulting_confidence"),
                resultSet.getString("operator_id"),
                resultSet.getString("justification"),
                JdbcCatalogQueryMappings.instant(resultSet, "decision_created_at"));
    }

    private DecisionAnomalyView mapDecisionAnomaly(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DecisionAnomalyView(
                mapAnomaly(resultSet),
                JdbcCatalogQueryMappings.instant(resultSet, "correlation_created_at"));
    }

    private AnomalyView mapAnomaly(ResultSet resultSet) throws SQLException {
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

    private ProviderMappingKey mappingKey(ResultSet resultSet) throws SQLException {
        return new ProviderMappingKey(
                resultSet.getString("provider"),
                ProviderEntityType.valueOf(resultSet.getString("entity_type")),
                resultSet.getString("provider_entity_id"),
                resultSet.getString("season"),
                resultSet.getString("phase"));
    }

    private static Double doubleOrNull(ResultSet resultSet, String column) throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    private static String nameOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
