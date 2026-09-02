package com.bettingproject.catalog.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.NormalizationReplayOrigin;
import com.bettingproject.catalog.application.NormalizationReplayRequest;
import com.bettingproject.catalog.application.NormalizationReplayRequestRepository;
import com.bettingproject.catalog.application.NormalizationReplaySelectorType;
import com.bettingproject.catalog.application.NormalizationReplayStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcNormalizationReplayRequestRepository implements NormalizationReplayRequestRepository {

    private final JdbcClient jdbcClient;

    public JdbcNormalizationReplayRequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<NormalizationReplayRequest> findById(UUID requestId) {
        return jdbcClient.sql(BASE_SELECT + " WHERE id = :requestId")
                .param("requestId", requestId)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<NormalizationReplayRequest> findByControlCommandReceiptId(UUID receiptId) {
        return jdbcClient.sql(BASE_SELECT + " WHERE control_command_receipt_id = :receiptId")
                .param("receiptId", receiptId)
                .query(this::map)
                .optional();
    }

    @Override
    public List<NormalizationReplayRequest> findByProviderMappingDecisionId(UUID decisionId) {
        return jdbcClient.sql(BASE_SELECT + """
                 WHERE provider_mapping_decision_id = :decisionId
                 ORDER BY selector_value, id
                """)
                .param("decisionId", decisionId)
                .query(this::map)
                .list();
    }

    @Override
    public void insert(NormalizationReplayRequest request) {
        jdbcClient.sql("""
                INSERT INTO normalization_replay_request (
                    id, control_command_receipt_id, raw_snapshot_id,
                    expected_payload_sha256, provider_mapping_decision_id,
                    origin, selector_type, selector_value, status, version,
                    attempt_count, last_error_code, last_error_message,
                    created_at, updated_at, completed_at
                ) VALUES (
                    :id, :receiptId, :snapshotId,
                    :expectedPayloadSha256, :mappingDecisionId,
                    :origin, :selectorType, :selectorValue, :status, :version,
                    :attemptCount, :lastErrorCode, :lastErrorMessage,
                    :createdAt, :updatedAt, :completedAt
                )
                """)
                .param("id", request.id())
                .param("receiptId", request.controlCommandReceiptId())
                .param("snapshotId", request.rawSnapshotId())
                .param("expectedPayloadSha256", request.expectedPayloadSha256())
                .param("mappingDecisionId", request.providerMappingDecisionId())
                .param("origin", request.origin().name())
                .param("selectorType", request.selectorType().name())
                .param("selectorValue", request.selectorValue())
                .param("status", request.status().name())
                .param("version", request.version())
                .param("attemptCount", request.attemptCount())
                .param("lastErrorCode", request.lastErrorCode())
                .param("lastErrorMessage", request.lastErrorMessage())
                .param("createdAt", utc(request.createdAt()))
                .param("updatedAt", utc(request.updatedAt()))
                .param("completedAt", utcOrNull(request.completedAt()))
                .update();
    }

    @Override
    public boolean claim(UUID requestId, long expectedVersion, Instant claimedAt) {
        return jdbcClient.sql("""
                UPDATE normalization_replay_request
                SET status = 'RUNNING',
                    version = version + 1,
                    attempt_count = attempt_count + 1,
                    last_error_code = NULL,
                    last_error_message = NULL,
                    updated_at = :claimedAt,
                    completed_at = NULL
                WHERE id = :requestId
                  AND version = :expectedVersion
                  AND status IN ('PENDING', 'FAILED_RETRYABLE')
                """)
                .param("requestId", requestId)
                .param("expectedVersion", expectedVersion)
                .param("claimedAt", utc(claimedAt))
                .update() == 1;
    }

    @Override
    public boolean complete(UUID requestId, long expectedVersion, Instant completedAt) {
        return jdbcClient.sql("""
                UPDATE normalization_replay_request
                SET status = 'COMPLETED',
                    version = version + 1,
                    last_error_code = NULL,
                    last_error_message = NULL,
                    updated_at = :completedAt,
                    completed_at = :completedAt
                WHERE id = :requestId
                  AND version = :expectedVersion
                  AND status = 'RUNNING'
                """)
                .param("requestId", requestId)
                .param("expectedVersion", expectedVersion)
                .param("completedAt", utc(completedAt))
                .update() == 1;
    }

    @Override
    public boolean fail(
            UUID requestId,
            long expectedVersion,
            NormalizationReplayStatus failureStatus,
            String errorCode,
            String errorMessage,
            Instant failedAt) {
        if (failureStatus != NormalizationReplayStatus.FAILED_RETRYABLE
                && failureStatus != NormalizationReplayStatus.FAILED_TERMINAL) {
            throw new IllegalArgumentException("failureStatus must describe a replay failure");
        }
        return jdbcClient.sql("""
                UPDATE normalization_replay_request
                SET status = :failureStatus,
                    version = version + 1,
                    last_error_code = :errorCode,
                    last_error_message = :errorMessage,
                    updated_at = :failedAt,
                    completed_at = CASE
                        WHEN :failureStatus = 'FAILED_TERMINAL' THEN :failedAt
                        ELSE NULL
                    END
                WHERE id = :requestId
                  AND version = :expectedVersion
                  AND status = 'RUNNING'
                """)
                .param("requestId", requestId)
                .param("expectedVersion", expectedVersion)
                .param("failureStatus", failureStatus.name())
                .param("errorCode", errorCode)
                .param("errorMessage", errorMessage)
                .param("failedAt", utc(failedAt))
                .update() == 1;
    }

    private NormalizationReplayRequest map(ResultSet resultSet, int rowNumber) throws SQLException {
        OffsetDateTime completedAt = resultSet.getObject("completed_at", OffsetDateTime.class);
        return new NormalizationReplayRequest(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("control_command_receipt_id", UUID.class),
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
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant(),
                completedAt == null ? null : completedAt.toInstant());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static OffsetDateTime utcOrNull(Instant instant) {
        return instant == null ? null : utc(instant);
    }

    private static final String BASE_SELECT = """
            SELECT id, control_command_receipt_id, raw_snapshot_id,
                   expected_payload_sha256, provider_mapping_decision_id,
                   origin, selector_type, selector_value, status, version,
                   attempt_count, last_error_code, last_error_message,
                   created_at, updated_at, completed_at
            FROM normalization_replay_request
            """;
}
