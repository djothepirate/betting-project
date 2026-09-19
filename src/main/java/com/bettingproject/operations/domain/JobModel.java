package com.bettingproject.operations.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The execution vocabulary is independent of providers and persistence. */
public final class JobModel {
    private JobModel() { }

    public enum Type {
        CALENDAR_DISCOVERY, PREMATCH_ENRICHMENT, POSTMATCH_ENRICHMENT, POSTMATCH_RECHECK, REPLAY_NORMALIZATION;

        public boolean executable() {
            return this == CALENDAR_DISCOVERY || this == REPLAY_NORMALIZATION;
        }
    }

    public enum Status { PENDING, RUNNING, SUCCEEDED, RETRY, FAILED, CANCELLED }

    public record Submission(String key, Type type, String contentSha256, Instant dueAt, int maxAttempts) {
        public Submission {
            text(key, 240);
            Objects.requireNonNull(type);
            if (!type.executable()) { throw new IllegalArgumentException("Unsupported job type"); }
            if (contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid job fingerprint");
            }
            Objects.requireNonNull(dueAt);
            if (maxAttempts < 1 || maxAttempts > 10) { throw new IllegalArgumentException("Invalid attempt bound"); }
        }
    }

    public record Job(UUID id, String key, Type type, String contentSha256, Status status,
            int attempts, int maxAttempts, long version, Instant dueAt, Instant leaseUntil, UUID token) { }

    public record Claim(Job job) {
        public Claim {
            Objects.requireNonNull(job);
            if (job.status() != Status.RUNNING || job.token() == null || job.leaseUntil() == null
                    || job.attempts() < 1 || job.attempts() > job.maxAttempts()) {
                throw new IllegalArgumentException("Invalid claim");
            }
        }
    }

    public record Enqueued(Job job, boolean created) { }

    public record Outcome(boolean successful, boolean retryable, String code) {
        public Outcome {
            if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}") || successful && retryable) {
                throw new IllegalArgumentException("Invalid job outcome");
            }
        }
        public static Outcome success() { return new Outcome(true, false, "COMPLETED"); }
        public static Outcome failed(String code) { return new Outcome(false, false, code); }
        public static Outcome retry(String code) { return new Outcome(false, true, code); }
    }

    public static Duration backoff(int attempt) {
        if (attempt < 1 || attempt > 10) { throw new IllegalArgumentException("Invalid attempt"); }
        return Duration.ofSeconds(Math.min(300, 5L << (attempt - 1)));
    }

    public static void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid job value");
        }
    }
}
