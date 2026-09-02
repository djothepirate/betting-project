package com.bettingproject.catalog.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.bettingproject.catalog.application.NormalizationReplayAttempt;
import com.bettingproject.catalog.application.NormalizationReplayAttemptJournal;
import com.bettingproject.catalog.application.NormalizationReplayAttemptOutcome;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcNormalizationReplayAttemptJournal implements NormalizationReplayAttemptJournal {

    private final JdbcClient jdbcClient;

    public JdbcNormalizationReplayAttemptJournal(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void append(NormalizationReplayAttempt attempt) {
        jdbcClient.sql("""
                INSERT INTO normalization_replay_attempt (
                    id, normalization_replay_request_id, attempt_number, outcome,
                    expected_payload_sha256, actual_payload_sha256, compatible,
                    fixtures_created, fixtures_updated, fixtures_unchanged,
                    fixtures_blocked, anomalies, error_code, error_message,
                    started_at, finished_at
                ) VALUES (
                    :id, :requestId, :attemptNumber, :outcome,
                    :expectedPayloadSha256, :actualPayloadSha256, :compatible,
                    :fixturesCreated, :fixturesUpdated, :fixturesUnchanged,
                    :fixturesBlocked, :anomalies, :errorCode, :errorMessage,
                    :startedAt, :finishedAt
                )
                """)
                .param("id", attempt.id())
                .param("requestId", attempt.requestId())
                .param("attemptNumber", attempt.attemptNumber())
                .param("outcome", attempt.outcome().name())
                .param("expectedPayloadSha256", attempt.expectedPayloadSha256())
                .param("actualPayloadSha256", attempt.actualPayloadSha256())
                .param("compatible", attempt.compatible())
                .param("fixturesCreated", attempt.fixturesCreated())
                .param("fixturesUpdated", attempt.fixturesUpdated())
                .param("fixturesUnchanged", attempt.fixturesUnchanged())
                .param("fixturesBlocked", attempt.fixturesBlocked())
                .param("anomalies", attempt.anomalies())
                .param("errorCode", attempt.errorCode())
                .param("errorMessage", attempt.errorMessage())
                .param("startedAt", attempt.startedAt().atOffset(ZoneOffset.UTC))
                .param("finishedAt", attempt.finishedAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public List<NormalizationReplayAttempt> findByRequestId(UUID requestId) {
        return jdbcClient.sql("""
                SELECT id, normalization_replay_request_id, attempt_number, outcome,
                       expected_payload_sha256, actual_payload_sha256, compatible,
                       fixtures_created, fixtures_updated, fixtures_unchanged,
                       fixtures_blocked, anomalies, error_code, error_message,
                       started_at, finished_at
                FROM normalization_replay_attempt
                WHERE normalization_replay_request_id = :requestId
                ORDER BY attempt_number, id
                """)
                .param("requestId", requestId)
                .query(this::map)
                .list();
    }

    private NormalizationReplayAttempt map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new NormalizationReplayAttempt(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("normalization_replay_request_id", UUID.class),
                resultSet.getInt("attempt_number"),
                NormalizationReplayAttemptOutcome.valueOf(resultSet.getString("outcome")),
                resultSet.getString("expected_payload_sha256"),
                resultSet.getString("actual_payload_sha256"),
                nullableBoolean(resultSet, "compatible"),
                nullableInteger(resultSet, "fixtures_created"),
                nullableInteger(resultSet, "fixtures_updated"),
                nullableInteger(resultSet, "fixtures_unchanged"),
                nullableInteger(resultSet, "fixtures_blocked"),
                nullableInteger(resultSet, "anomalies"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                resultSet.getObject("started_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("finished_at", OffsetDateTime.class).toInstant());
    }

    private static Boolean nullableBoolean(ResultSet resultSet, String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
