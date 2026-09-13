package com.bettingproject.collection.domain.budget;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable budget vocabulary. No supplier call or persistence mechanism belongs here. */
public final class BudgetModel {
    public static final long MAX_UNITS = 1_000_000_000L;

    private BudgetModel() {
    }

    public enum WindowState { ACTIVE, SUSPENDED, CLOSED }
    public enum IntentState { RESERVED, COMMITTED_FOR_SEND, RESULT_RECORDED, UNCERTAIN, RECONCILED, RELEASED }
    public enum ObservationDisposition { ACCEPTED, STALE, CONTRADICTORY }
    public enum ResultCode {
        OK, WINDOW_UNINITIALIZED, OUTSIDE_WINDOW, SUSPENDED, QUOTA_OBSERVATION_MISSING,
        STALE_OBSERVATION, CONTRADICTORY_OBSERVATION, EXHAUSTED, RATE_LIMITED,
        IDEMPOTENCY_CONFLICT, ALREADY_COMMITTED, INVALID_TRANSITION, NOT_FOUND,
        VERSION_CONFLICT, OPERATOR_UNAVAILABLE, INVALID_COMMAND
    }

    public record Scope(UUID id, String provider, String accountRef, Instant createdAt) {
        public Scope {
            required(id);
            provider = exact(provider, 64);
            accountRef = exact(accountRef, 128);
            required(createdAt);
        }
    }

    public record Proof(String logicalId, String sha256) {
        public Proof {
            logicalId = exact(logicalId, 200);
            sha256 = hash(sha256);
        }
    }

    public record Window(
            UUID id, UUID scopeId, Instant startsAt, Instant endsAt, Long capacity,
            long projectLimit, long reserve, long sharedInitial, long projectInitial,
            Integer cadenceLimit, Duration cadencePeriod, WindowState state,
            UUID currentObservationId, boolean quotaInconsistent, long version,
            Instant createdAt, Instant updatedAt, Proof proof) {
        public Window {
            required(id);
            required(scopeId);
            required(startsAt);
            required(endsAt);
            required(state);
            required(proof);
            timestamps(createdAt, updatedAt);
            positiveVersion(version);
            positiveUnits(projectLimit);
            units(reserve);
            units(sharedInitial);
            units(projectInitial);
            if (!startsAt.isBefore(endsAt)) {
                throw invalid();
            }
            if (capacity != null) {
                positiveUnits(capacity);
                if (reserve > capacity || sharedInitial > capacity || projectInitial > sharedInitial) {
                    throw invalid();
                }
            }
            else if (reserve != 0 || sharedInitial != 0 || cadenceLimit == null
                    || currentObservationId != null || quotaInconsistent) {
                throw invalid();
            }
            if ((cadenceLimit == null) != (cadencePeriod == null)) {
                throw invalid();
            }
            if (cadenceLimit != null) {
                positiveUnits(cadenceLimit);
                if (cadencePeriod.compareTo(Duration.ofSeconds(1)) < 0
                        || cadencePeriod.compareTo(Duration.ofDays(365)) > 0
                        || cadencePeriod.getNano() % 1_000_000 != 0) {
                    throw invalid();
                }
            }
        }

        public Window withObservation(UUID observationId, boolean inconsistent, Instant now) {
            return new Window(id, scopeId, startsAt, endsAt, capacity, projectLimit, reserve,
                    sharedInitial, projectInitial, cadenceLimit, cadencePeriod, state,
                    observationId, inconsistent, nextVersion(version), createdAt, now, proof);
        }

        public Window suspend(Instant now) {
            if (state != WindowState.ACTIVE) {
                throw invalid();
            }
            return withState(WindowState.SUSPENDED, now);
        }

        public Window close(Instant now) {
            if (state == WindowState.CLOSED) {
                throw invalid();
            }
            return withState(WindowState.CLOSED, now);
        }

        private Window withState(WindowState next, Instant now) {
            return new Window(id, scopeId, startsAt, endsAt, capacity, projectLimit, reserve,
                    sharedInitial, projectInitial, cadenceLimit, cadencePeriod, next,
                    currentObservationId, quotaInconsistent, nextVersion(version), createdAt, now, proof);
        }
    }

    public record Intent(
            UUID id, UUID windowId, String idempotencyKey, String logicalEndpoint,
            String requestSha256, IntentState state, long version, Instant createdAt,
            Instant updatedAt, Instant committedAt, Instant resultAt, Integer httpStatus,
            String resultFingerprint) {
        public Intent {
            required(id);
            required(windowId);
            idempotencyKey = visibleAscii(idempotencyKey, 128);
            logicalEndpoint = exact(logicalEndpoint, 200);
            if (logicalEndpoint.startsWith("/") || logicalEndpoint.chars().anyMatch(character ->
                    character == ':' || character == '?' || character == '#' || character == '&'
                            || character == '=' || character == '\\')) {
                throw invalid();
            }
            requestSha256 = hash(requestSha256);
            required(state);
            positiveVersion(version);
            timestamps(createdAt, updatedAt);
            boolean beforeSend = state == IntentState.RESERVED || state == IntentState.RELEASED;
            boolean finalResult = state == IntentState.RESULT_RECORDED || state == IntentState.RECONCILED;
            if (beforeSend != (committedAt == null) || finalResult != (resultAt != null)) {
                throw invalid();
            }
            if (committedAt != null && (committedAt.isBefore(createdAt) || committedAt.isAfter(updatedAt))) {
                throw invalid();
            }
            if (resultAt != null && (resultAt.isBefore(committedAt) || resultAt.isAfter(updatedAt))) {
                throw invalid();
            }
            if (state == IntentState.RESULT_RECORDED) {
                if (httpStatus == null || httpStatus < 100 || httpStatus > 599) {
                    throw invalid();
                }
                resultFingerprint = hash(resultFingerprint);
            }
            else if (httpStatus != null || (!finalResult && resultFingerprint != null)) {
                throw invalid();
            }
            else if (resultFingerprint != null) {
                resultFingerprint = hash(resultFingerprint);
            }
        }

        public boolean held() {
            return state == IntentState.RESERVED;
        }

        public boolean consumed() {
            return state != IntentState.RESERVED && state != IntentState.RELEASED;
        }

        public Intent transition(IntentState next, Instant now, Integer status, String fingerprint) {
            required(next);
            boolean allowed = switch (state) {
                case RESERVED -> next == IntentState.RELEASED || next == IntentState.COMMITTED_FOR_SEND;
                case COMMITTED_FOR_SEND -> next == IntentState.RESULT_RECORDED || next == IntentState.UNCERTAIN;
                case UNCERTAIN -> next == IntentState.RECONCILED;
                case RELEASED, RESULT_RECORDED, RECONCILED -> false;
            };
            if (!allowed) {
                throw invalid();
            }
            Instant commitTime = next == IntentState.COMMITTED_FOR_SEND ? now : committedAt;
            Instant resultTime = next == IntentState.RESULT_RECORDED || next == IntentState.RECONCILED ? now : null;
            return new Intent(id, windowId, idempotencyKey, logicalEndpoint, requestSha256,
                    next, nextVersion(version), createdAt, now, commitTime, resultTime, status, fingerprint);
        }
    }

    public record QuotaObservation(
            UUID id, UUID windowId, long remaining, Instant observedAt, Instant validUntil,
            Proof proof, Set<UUID> coveredIntentIds, ObservationDisposition disposition, Instant createdAt) {
        public QuotaObservation {
            required(id);
            required(windowId);
            units(remaining);
            required(observedAt);
            required(validUntil);
            required(proof);
            coveredIntentIds = Set.copyOf(required(coveredIntentIds));
            required(disposition);
            required(createdAt);
            if (!observedAt.isBefore(validUntil) || observedAt.isAfter(createdAt)) {
                throw invalid();
            }
        }
    }

    public record Incident(UUID id, UUID windowId, UUID intentId, String code, Instant createdAt) {
        public Incident {
            required(id);
            required(windowId);
            code = BudgetModel.code(code);
            required(createdAt);
        }
    }

    public record Event(
            UUID id, UUID scopeId, UUID windowId, UUID intentId, String type,
            String reasonCode, String operatorId, String justification, Proof proof, Instant createdAt) {
        public Event {
            required(id);
            required(scopeId);
            if (intentId != null && windowId == null) {
                throw invalid();
            }
            type = code(type);
            reasonCode = reasonCode == null ? null : code(reasonCode);
            operatorId = operatorId == null ? null : exact(operatorId, 100);
            justification = justification == null ? null : exact(justification, 1000);
            required(createdAt);
        }
    }

    public record Availability(ResultCode code, long available, Instant retryAt) {
        public Availability {
            required(code);
            units(available);
            if (code != ResultCode.OK && available != 0) {
                throw invalid();
            }
            if (retryAt != null && code != ResultCode.RATE_LIMITED) {
                throw invalid();
            }
        }
    }

    private static <T> T required(T value) {
        return Objects.requireNonNull(value, "required budget value");
    }

    private static void timestamps(Instant created, Instant updated) {
        required(created);
        required(updated);
        if (updated.isBefore(created)) {
            throw invalid();
        }
    }

    private static void units(long value) {
        if (value < 0 || value > MAX_UNITS) {
            throw invalid();
        }
    }

    private static void positiveUnits(long value) {
        units(value);
        if (value == 0) {
            throw invalid();
        }
    }

    private static void positiveVersion(long value) {
        if (value < 1) {
            throw invalid();
        }
    }

    private static long nextVersion(long version) {
        if (version == Long.MAX_VALUE) {
            throw invalid();
        }
        return version + 1;
    }

    private static String exact(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > maximum || !value.equals(value.strip())
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        return value;
    }

    private static String visibleAscii(String value, int maximum) {
        exact(value, maximum);
        if (!value.chars().allMatch(character -> character >= 33 && character <= 126)) {
            throw invalid();
        }
        return value;
    }

    private static String hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw invalid();
        }
        return value;
    }

    private static String code(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw invalid();
        }
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid budget value or transition");
    }
}
