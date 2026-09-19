package com.bettingproject.operations.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.bettingproject.operations.application.jobs.JobIdempotencyConflictException;
import com.bettingproject.operations.application.jobs.JobLeaseLostException;
import com.bettingproject.operations.application.jobs.JobRepository;
import com.bettingproject.operations.domain.JobModel;
import com.bettingproject.operations.domain.JobModel.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"control-api", "batch-worker"})
@Transactional
public class JdbcJobRepository implements JobRepository {
    private final JdbcClient jdbc;
    public JdbcJobRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public Instant databaseNow() {
        return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant();
    }

    @Override public Enqueued enqueue(Submission submission) {
        UUID id = UUID.randomUUID();
        Instant now = databaseNow();
        Instant due = submission.dueAt().truncatedTo(ChronoUnit.MICROS);
        int inserted = jdbc.sql("""
                INSERT INTO persistent_job (id, job_key, job_type, status, attempts, next_run_at,
                    created_at, updated_at, managed_collection, command_sha256, scheduled_at, max_attempts)
                VALUES (:id, :key, :type, 'PENDING', 0, :due, :now, :now, TRUE, :hash, :due, :max)
                ON CONFLICT (job_key) DO NOTHING
                """).param("id", id).param("key", submission.key()).param("type", submission.type().name())
                .param("due", utc(due)).param("now", utc(now)).param("hash", submission.contentSha256())
                .param("max", submission.maxAttempts()).update();
        // A second statement resolves the winner after a concurrent INSERT's commit.
        var row = jdbc.sql("SELECT id, managed_collection, scheduled_at FROM persistent_job WHERE job_key = :key")
                .param("key", submission.key()).query((rs, n) -> new Object[] {
                    rs.getObject("id", UUID.class), rs.getBoolean("managed_collection"), time(rs, "scheduled_at")
                }).single();
        if (!Boolean.TRUE.equals(row[1])) { throw new JobIdempotencyConflictException(); }
        Job stored = find((UUID) row[0]).orElseThrow();
        if (stored.type() != submission.type() || !stored.contentSha256().equals(submission.contentSha256())
                || stored.maxAttempts() != submission.maxAttempts() || !due.equals(row[2])) {
            throw new JobIdempotencyConflictException();
        }
        if (inserted == 1) {
            jdbc.sql("""
                    INSERT INTO outbox_message (id, idempotency_key, aggregate_type, aggregate_id, destination,
                        payload_json, status, attempt_count, next_attempt_at, created_at, updated_at)
                    VALUES (:id, :key, 'persistent_job', :job, 'COLLECTION_JOB',
                        jsonb_build_object('jobId', CAST(:job AS text)), 'PENDING', 0, :due, :now, :now)
                    """).param("id", UUID.randomUUID()).param("key", key(stored.id())).param("job", stored.id())
                    .param("due", utc(due)).param("now", utc(now)).update();
            event(stored, "ENQUEUED", null, now);
        }
        return new Enqueued(stored, inserted == 1);
    }

    @Override public Optional<Job> find(UUID id) {
        return jdbc.sql("SELECT * FROM persistent_job WHERE id = :id AND managed_collection")
                .param("id", id).query(this::job).optional();
    }

    @Override public Optional<Claim> claimNext() {
        var candidate = jdbc.sql("""
                SELECT j.* FROM persistent_job j
                JOIN outbox_message o ON o.idempotency_key = 'collection-job:' || j.id::text
                WHERE j.managed_collection AND j.status IN ('PENDING', 'RETRY')
                  AND j.next_run_at <= clock_timestamp() AND j.attempts < j.max_attempts
                  AND o.destination = 'COLLECTION_JOB' AND o.aggregate_id = j.id
                  AND o.status IN ('PENDING', 'RETRY')
                ORDER BY j.next_run_at, j.created_at, j.id LIMIT 1 FOR UPDATE OF j SKIP LOCKED
                """).query(this::job).optional();
        if (candidate.isEmpty()) { return Optional.empty(); }
        Job before = candidate.get();
        Instant now = databaseNow();
        UUID token = UUID.randomUUID();
        int changed = jdbc.sql("""
                UPDATE persistent_job SET status = 'RUNNING', attempts = attempts + 1,
                    execution_version = execution_version + 1, lease_token = :token, lease_until = :lease,
                    started_at = :now, finished_at = NULL, updated_at = :now, last_error_code = NULL,
                    last_error_message = NULL
                WHERE id = :id AND execution_version = :version
                """).param("token", token).param("lease", utc(now.plusSeconds(120))).param("now", utc(now))
                .param("id", before.id()).param("version", before.version()).update();
        requireOne(changed);
        Job claimed = find(before.id()).orElseThrow();
        outbox(claimed, "SENDING", null, null, now);
        event(claimed, "CLAIMED", null, now);
        return Optional.of(new Claim(claimed));
    }

    @Override public int recoverExpired(int limit) {
        var expired = jdbc.sql("""
                SELECT * FROM persistent_job WHERE managed_collection AND status = 'RUNNING'
                  AND lease_until <= clock_timestamp()
                ORDER BY lease_until, id LIMIT :limit FOR UPDATE SKIP LOCKED
                """).param("limit", limit).query(this::job).list();
        for (Job job : expired) { end(job, Outcome.retry("LEASE_EXPIRED"), "LEASE_EXPIRED"); }
        return expired.size();
    }

    @Override public void lockAndCheck(Claim claim) {
        var current = jdbc.sql("SELECT * FROM persistent_job WHERE id = :id AND managed_collection FOR UPDATE")
                .param("id", claim.job().id()).query(this::job).optional();
        Instant now = databaseNow();
        if (current.isEmpty() || current.get().status() != Status.RUNNING
                || !Objects.equals(current.get().token(), claim.job().token())
                || !current.get().leaseUntil().isAfter(now)) {
            throw new JobLeaseLostException();
        }
        // Heartbeat only at a guarded DB boundary, never on a separate unowned background loop.
        requireOne(jdbc.sql("UPDATE persistent_job SET lease_until = :lease, updated_at = :now WHERE id = :id AND lease_token = :token")
                .param("lease", utc(now.plusSeconds(120))).param("now", utc(now))
                .param("id", current.get().id()).param("token", claim.job().token()).update());
    }

    @Override public void finish(Claim claim, Outcome outcome) {
        lockAndCheck(claim);
        end(find(claim.job().id()).orElseThrow(), outcome, null);
    }

    private void end(Job before, Outcome outcome, String eventOverride) {
        Instant now = databaseNow();
        boolean retry = outcome.retryable() && before.attempts() < before.maxAttempts();
        Status status = outcome.successful() ? Status.SUCCEEDED : retry ? Status.RETRY : Status.FAILED;
        String code = outcome.successful() ? null : outcome.retryable() && !retry ? "ATTEMPTS_EXHAUSTED" : outcome.code();
        Instant next = retry ? now.plus(JobModel.backoff(before.attempts())) : null;
        requireOne(jdbc.sql("""
                UPDATE persistent_job SET status = :status, execution_version = execution_version + 1,
                    lease_token = NULL, lease_until = NULL, next_run_at = :next,
                    finished_at = :finished, last_error_code = :code, last_error_message = NULL, updated_at = :now
                WHERE id = :id AND status = 'RUNNING' AND lease_token = :token AND execution_version = :version
                """).param("status", status.name()).param("next", utc(next)).param("finished", retry ? null : utc(now))
                .param("code", code).param("now", utc(now)).param("id", before.id()).param("token", before.token())
                .param("version", before.version()).update());
        Job after = find(before.id()).orElseThrow();
        outbox(after, outcome.successful() ? "DELIVERED" : retry ? "RETRY" : "FAILED", next, code, now);
        event(new Job(after.id(), after.key(), after.type(), after.contentSha256(), after.status(),
                after.attempts(), after.maxAttempts(), after.version(), after.dueAt(), before.leaseUntil(), before.token()),
                eventOverride == null ? status.name() : eventOverride, code, now);
    }

    private void outbox(Job job, String status, Instant next, String code, Instant now) {
        requireOne(jdbc.sql("""
                UPDATE outbox_message SET status = :status, attempt_count = :attempts, next_attempt_at = :next,
                    delivered_at = :delivered, last_error_code = :code, updated_at = :now
                WHERE idempotency_key = :key AND destination = 'COLLECTION_JOB' AND aggregate_id = :job
                """).param("status", status).param("attempts", job.attempts()).param("next", utc(next))
                .param("delivered", "DELIVERED".equals(status) ? utc(now) : null).param("code", code)
                .param("now", utc(now)).param("key", key(job.id())).param("job", job.id()).update());
    }

    private void event(Job job, String type, String code, Instant now) {
        jdbc.sql("""
                INSERT INTO collection_job_event (id, job_id, attempt_number, execution_version,
                    lease_token, event_type, reason_code, created_at)
                VALUES (:id, :job, :attempt, :version, :token, :type, :code, :now)
                """).param("id", UUID.randomUUID()).param("job", job.id()).param("attempt", job.attempts())
                .param("version", job.version()).param("token", job.token()).param("type", type)
                .param("code", code).param("now", utc(now)).update();
    }

    @Override public Optional<String> effectResult(UUID jobId, String effectKey) {
        return jdbc.sql("SELECT result_code FROM collection_job_effect WHERE job_id = :job AND effect_key = :key")
                .param("job", jobId).param("key", effectKey).query(String.class).optional();
    }

    @Override public void appendEffect(UUID jobId, String effectKey, String result) {
        jdbc.sql("""
                INSERT INTO collection_job_effect (job_id, effect_key, result_code, created_at)
                VALUES (:job, :key, :result, clock_timestamp())
                """).param("job", jobId).param("key", effectKey).param("result", result).update();
    }

    private Job job(ResultSet rs, int row) throws SQLException {
        return new Job(rs.getObject("id", UUID.class), rs.getString("job_key"), Type.valueOf(rs.getString("job_type")),
                rs.getString("command_sha256"), Status.valueOf(rs.getString("status")), rs.getInt("attempts"),
                rs.getInt("max_attempts"), rs.getLong("execution_version"), time(rs, "next_run_at"),
                time(rs, "lease_until"), rs.getObject("lease_token", UUID.class));
    }

    private static Instant time(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
    private static OffsetDateTime utc(Instant value) { return value == null ? null : value.atOffset(ZoneOffset.UTC); }
    private static String key(UUID id) { return "collection-job:" + id; }
    private static void requireOne(int count) {
        if (count != 1) { throw new IllegalStateException("Collection job persistence invariant"); }
    }
}
