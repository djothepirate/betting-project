package com.bettingproject.catalog.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.AttemptKeysetAnchor;
import com.bettingproject.catalog.application.CorrelationKeysetAnchor;
import com.bettingproject.catalog.application.NormalizationReplayAttemptOutcome;
import com.bettingproject.catalog.application.NormalizationReplayOrigin;
import com.bettingproject.catalog.application.NormalizationReplaySelectorType;
import com.bettingproject.catalog.application.NormalizationReplayStatus;
import com.bettingproject.catalog.application.ReplayQueryPort;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayAnomalyEventView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayApplicationView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayAttemptView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayQueryFilter;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayRequestView;
import com.bettingproject.catalog.application.TimestampKeysetAnchor;
import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import com.bettingproject.catalog.domain.FixtureApplicationOutcome;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyEventType;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcReplayQueryAdapter implements ReplayQueryPort {

    private static final String REQUEST_SELECT = """
            SELECT r.id, r.raw_snapshot_id, r.expected_payload_sha256,
                   r.provider_mapping_decision_id, r.origin, r.selector_type,
                   r.selector_value, r.status, r.version, r.attempt_count,
                   r.last_error_code, r.last_error_message, r.created_at,
                   r.updated_at, r.completed_at,
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
            FROM normalization_replay_request r
            JOIN raw_snapshot s ON s.id = r.raw_snapshot_id
            """;

    private final JdbcClient jdbcClient;

    public JdbcReplayQueryAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<ReplayRequestView> fetchRequests(
            ReplayQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int fetchLimit) {
        return jdbcClient.sql(REQUEST_SELECT + """
                WHERE (CAST(:status AS VARCHAR) IS NULL OR r.status = :status)
                  AND (CAST(:origin AS VARCHAR) IS NULL OR r.origin = :origin)
                  AND (CAST(:snapshotId AS UUID) IS NULL OR r.raw_snapshot_id = :snapshotId)
                  AND (CAST(:decisionId AS UUID) IS NULL
                       OR r.provider_mapping_decision_id = :decisionId)
                  AND (
                      CAST(:anchorTimestamp AS TIMESTAMPTZ) IS NULL
                      OR (r.updated_at, r.id) < (:anchorTimestamp, :anchorId)
                  )
                ORDER BY r.updated_at DESC, r.id DESC
                LIMIT :fetchLimit
                """)
                .param("status", nameOrNull(filter.status()))
                .param("origin", nameOrNull(filter.origin()))
                .param("snapshotId", filter.rawSnapshotId())
                .param("decisionId", filter.providerMappingDecisionId())
                .param("anchorTimestamp", anchor == null ? null : anchor.timestamp().atOffset(ZoneOffset.UTC))
                .param("anchorId", anchor == null ? null : anchor.id())
                .param("fetchLimit", fetchLimit)
                .query(this::mapRequest)
                .list();
    }

    @Override
    public Optional<ReplayRequestView> findRequest(UUID requestId) {
        return jdbcClient.sql(REQUEST_SELECT + " WHERE r.id = :requestId")
                .param("requestId", requestId)
                .query(this::mapRequest)
                .optional();
    }

    @Override
    public List<ReplayAttemptView> fetchAttempts(
            UUID requestId,
            AttemptKeysetAnchor anchor,
            int fetchLimit) {
        return jdbcClient.sql("""
                SELECT a.id, a.normalization_replay_request_id, a.attempt_number,
                       a.outcome, a.expected_payload_sha256, a.actual_payload_sha256,
                       a.compatible, a.fixtures_created, a.fixtures_updated,
                       a.fixtures_unchanged, a.fixtures_blocked, a.anomalies,
                       a.error_code, a.error_message, a.started_at, a.finished_at
                FROM normalization_replay_attempt a
                WHERE a.normalization_replay_request_id = :requestId
                  AND (
                      CAST(:anchorAttempt AS INTEGER) IS NULL
                      OR (a.attempt_number, a.id) < (:anchorAttempt, :anchorId)
                  )
                ORDER BY a.attempt_number DESC, a.id DESC
                LIMIT :fetchLimit
                """)
                .param("requestId", requestId)
                .param("anchorAttempt", anchor == null ? null : anchor.attemptNumber())
                .param("anchorId", anchor == null ? null : anchor.id())
                .param("fetchLimit", fetchLimit)
                .query(this::mapAttempt)
                .list();
    }

    @Override
    public List<ReplayApplicationView> fetchApplications(
            UUID requestId,
            UUID attemptId,
            CorrelationKeysetAnchor anchor,
            int fetchLimit) {
        return jdbcClient.sql("""
                SELECT l.id, l.fixture_observation_id, l.canonical_fixture_id,
                       l.previous_authority_observation_id, l.outcome, l.reason_code,
                       l.authority_role, l.policy_version, l.evaluated_at,
                       c.created_at AS correlation_created_at
                FROM normalization_replay_attempt a
                JOIN normalization_replay_attempt_application c
                  ON c.normalization_replay_attempt_id = a.id
                JOIN fixture_application_log l ON l.id = c.fixture_application_log_id
                WHERE a.normalization_replay_request_id = :requestId
                  AND a.id = :attemptId
                  AND (
                      CAST(:anchorTimestamp AS TIMESTAMPTZ) IS NULL
                      OR (c.created_at, l.id) < (:anchorTimestamp, :anchorId)
                  )
                ORDER BY c.created_at DESC, l.id DESC
                LIMIT :fetchLimit
                """)
                .param("requestId", requestId)
                .param("attemptId", attemptId)
                .param("anchorTimestamp", anchor == null ? null : anchor.createdAt().atOffset(ZoneOffset.UTC))
                .param("anchorId", anchor == null ? null : anchor.targetId())
                .param("fetchLimit", fetchLimit)
                .query(this::mapApplication)
                .list();
    }

    @Override
    public List<ReplayAnomalyEventView> fetchAnomalyEvents(
            UUID requestId,
            UUID attemptId,
            CorrelationKeysetAnchor anchor,
            int fetchLimit) {
        return jdbcClient.sql("""
                SELECT e.id, e.normalization_anomaly_id, e.event_type,
                       e.previous_status, e.resulting_status,
                       e.fixture_application_log_id, e.details,
                       e.created_at AS event_created_at,
                       c.created_at AS correlation_created_at
                FROM normalization_replay_attempt a
                JOIN normalization_replay_attempt_anomaly_event c
                  ON c.normalization_replay_attempt_id = a.id
                JOIN normalization_anomaly_event e ON e.id = c.normalization_anomaly_event_id
                WHERE a.normalization_replay_request_id = :requestId
                  AND a.id = :attemptId
                  AND (
                      CAST(:anchorTimestamp AS TIMESTAMPTZ) IS NULL
                      OR (c.created_at, e.id) < (:anchorTimestamp, :anchorId)
                  )
                ORDER BY c.created_at DESC, e.id DESC
                LIMIT :fetchLimit
                """)
                .param("requestId", requestId)
                .param("attemptId", attemptId)
                .param("anchorTimestamp", anchor == null ? null : anchor.createdAt().atOffset(ZoneOffset.UTC))
                .param("anchorId", anchor == null ? null : anchor.targetId())
                .param("fetchLimit", fetchLimit)
                .query(this::mapAnomalyEvent)
                .list();
    }

    private ReplayRequestView mapRequest(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ReplayRequestView(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("raw_snapshot_id", UUID.class),
                resultSet.getString("expected_payload_sha256"),
                resultSet.getObject("provider_mapping_decision_id", UUID.class),
                NormalizationReplayOrigin.valueOf(resultSet.getString("origin")),
                NormalizationReplaySelectorType.valueOf(resultSet.getString("selector_type")),
                resultSet.getString("selector_value"),
                NormalizationReplayStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("version"),
                resultSet.getInt("attempt_count"),
                resultSet.getString("last_error_code"),
                resultSet.getString("last_error_message"),
                JdbcCatalogQueryMappings.instant(resultSet, "created_at"),
                JdbcCatalogQueryMappings.instant(resultSet, "updated_at"),
                JdbcCatalogQueryMappings.instantOrNull(resultSet, "completed_at"),
                JdbcCatalogQueryMappings.snapshot(resultSet));
    }

    private ReplayAttemptView mapAttempt(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ReplayAttemptView(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("normalization_replay_request_id", UUID.class),
                resultSet.getInt("attempt_number"),
                NormalizationReplayAttemptOutcome.valueOf(resultSet.getString("outcome")),
                resultSet.getString("expected_payload_sha256"),
                resultSet.getString("actual_payload_sha256"),
                resultSet.getObject("compatible", Boolean.class),
                resultSet.getObject("fixtures_created", Integer.class),
                resultSet.getObject("fixtures_updated", Integer.class),
                resultSet.getObject("fixtures_unchanged", Integer.class),
                resultSet.getObject("fixtures_blocked", Integer.class),
                resultSet.getObject("anomalies", Integer.class),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                JdbcCatalogQueryMappings.instant(resultSet, "started_at"),
                JdbcCatalogQueryMappings.instant(resultSet, "finished_at"));
    }

    private ReplayApplicationView mapApplication(ResultSet resultSet, int rowNumber) throws SQLException {
        String authorityRole = resultSet.getString("authority_role");
        return new ReplayApplicationView(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("fixture_observation_id", UUID.class),
                resultSet.getObject("canonical_fixture_id", UUID.class),
                resultSet.getObject("previous_authority_observation_id", UUID.class),
                FixtureApplicationOutcome.valueOf(resultSet.getString("outcome")),
                resultSet.getString("reason_code"),
                authorityRole == null ? null : CalendarAuthorityRole.valueOf(authorityRole),
                resultSet.getString("policy_version"),
                JdbcCatalogQueryMappings.instant(resultSet, "evaluated_at"),
                JdbcCatalogQueryMappings.instant(resultSet, "correlation_created_at"));
    }

    private ReplayAnomalyEventView mapAnomalyEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        String previousStatus = resultSet.getString("previous_status");
        return new ReplayAnomalyEventView(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("normalization_anomaly_id", UUID.class),
                NormalizationAnomalyEventType.valueOf(resultSet.getString("event_type")),
                previousStatus == null ? null : AnomalyStatus.valueOf(previousStatus),
                AnomalyStatus.valueOf(resultSet.getString("resulting_status")),
                resultSet.getObject("fixture_application_log_id", UUID.class),
                resultSet.getString("details"),
                JdbcCatalogQueryMappings.instant(resultSet, "event_created_at"),
                JdbcCatalogQueryMappings.instant(resultSet, "correlation_created_at"));
    }

    private static String nameOrNull(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
