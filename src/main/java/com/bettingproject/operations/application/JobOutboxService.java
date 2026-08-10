package com.bettingproject.operations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"control-api", "batch-worker"})
public class JobOutboxService {

    private final JdbcClient jdbcClient;

    public JobOutboxService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public boolean schedulePublication(
            String jobKey,
            String jobType,
            String destination,
            String payloadJson) {
        UUID jobId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int inserted = jdbcClient.sql("""
                INSERT INTO persistent_job (
                    id, job_key, job_type, status, attempts, next_run_at, created_at, updated_at
                ) VALUES (
                    :id, :jobKey, :jobType, 'PENDING', 0, :now, :now, :now
                )
                ON CONFLICT (job_key) DO NOTHING
                """)
                .param("id", jobId)
                .param("jobKey", jobKey)
                .param("jobType", jobType)
                .param("now", now)
                .update();
        if (inserted == 0) {
            return false;
        }

        jdbcClient.sql("""
                INSERT INTO outbox_message (
                    id, idempotency_key, aggregate_type, aggregate_id, destination,
                    payload_json, status, attempt_count, next_attempt_at, created_at, updated_at
                ) VALUES (
                    :id, :idempotencyKey, 'persistent_job', :aggregateId, :destination,
                    CAST(:payloadJson AS jsonb), 'PENDING', 0, :now, :now, :now
                )
                """)
                .param("id", UUID.randomUUID())
                .param("idempotencyKey", "job:" + jobKey + ":" + destination)
                .param("aggregateId", jobId)
                .param("destination", destination)
                .param("payloadJson", payloadJson)
                .param("now", now)
                .update();
        return true;
    }

    public long countJobs(String jobKey) {
        return jdbcClient.sql("SELECT COUNT(*) FROM persistent_job WHERE job_key = :jobKey")
                .param("jobKey", jobKey)
                .query(Long.class)
                .single();
    }

    public long countOutboxMessages(String idempotencyKey) {
        return jdbcClient.sql("SELECT COUNT(*) FROM outbox_message WHERE idempotency_key = :idempotencyKey")
                .param("idempotencyKey", idempotencyKey)
                .query(Long.class)
                .single();
    }
}
