package com.bettingproject.operations.adapter.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.bettingproject.operations.application.JobOutboxRepository;
import com.bettingproject.operations.application.PendingJob;
import com.bettingproject.operations.application.PendingOutboxMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcJobOutboxRepository implements JobOutboxRepository {

    private final JdbcClient jdbcClient;

    public JdbcJobOutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean insertJobIfAbsent(PendingJob job) {
        OffsetDateTime scheduledAt = job.scheduledAt().atOffset(ZoneOffset.UTC);
        int inserted = jdbcClient.sql("""
                INSERT INTO persistent_job (
                    id, job_key, job_type, status, attempts, next_run_at, created_at, updated_at
                ) VALUES (
                    :id, :jobKey, :jobType, 'PENDING', 0, :scheduledAt, :scheduledAt, :scheduledAt
                )
                ON CONFLICT (job_key) DO NOTHING
                """)
                .param("id", job.id())
                .param("jobKey", job.jobKey())
                .param("jobType", job.jobType())
                .param("scheduledAt", scheduledAt)
                .update();
        return inserted == 1;
    }

    @Override
    public void insertOutboxMessage(PendingOutboxMessage message) {
        OffsetDateTime scheduledAt = message.scheduledAt().atOffset(ZoneOffset.UTC);
        jdbcClient.sql("""
                INSERT INTO outbox_message (
                    id, idempotency_key, aggregate_type, aggregate_id, destination,
                    payload_json, status, attempt_count, next_attempt_at, created_at, updated_at
                ) VALUES (
                    :id, :idempotencyKey, 'persistent_job', :aggregateId, :destination,
                    CAST(:payloadJson AS jsonb), 'PENDING', 0,
                    :scheduledAt, :scheduledAt, :scheduledAt
                )
                """)
                .param("id", message.id())
                .param("idempotencyKey", message.idempotencyKey())
                .param("aggregateId", message.aggregateId())
                .param("destination", message.destination())
                .param("payloadJson", message.payloadJson())
                .param("scheduledAt", scheduledAt)
                .update();
    }
}
