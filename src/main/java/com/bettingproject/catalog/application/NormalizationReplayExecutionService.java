package com.bettingproject.catalog.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.collection.domain.SnapshotHasher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("control-api")
public class NormalizationReplayExecutionService {

    private final NormalizationReplayRequestRepository requestRepository;
    private final StoredRawSnapshotReader snapshotReader;
    private final NormalizationReplayAttemptJournal attemptJournal;
    private final NormalizationReplayApplicationStore applicationStore;
    private final NormalizationReplayAnomalyEventStore anomalyEventStore;
    private final CalendarNormalizationLock normalizationLock;
    private final CalendarNormalizationService normalizationService;
    private final NormalizationReplaySavepoint savepoint;
    private final Clock clock;

    public NormalizationReplayExecutionService(
            NormalizationReplayRequestRepository requestRepository,
            StoredRawSnapshotReader snapshotReader,
            NormalizationReplayAttemptJournal attemptJournal,
            NormalizationReplayApplicationStore applicationStore,
            NormalizationReplayAnomalyEventStore anomalyEventStore,
            CalendarNormalizationLock normalizationLock,
            CalendarNormalizationService normalizationService,
            NormalizationReplaySavepoint savepoint,
            Clock clock) {
        this.requestRepository = requestRepository;
        this.snapshotReader = snapshotReader;
        this.attemptJournal = attemptJournal;
        this.applicationStore = applicationStore;
        this.anomalyEventStore = anomalyEventStore;
        this.normalizationLock = normalizationLock;
        this.normalizationService = normalizationService;
        this.savepoint = savepoint;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NormalizationReplayExecutionResult executeImmediately(UUID requestId) {
        return execute(requestId, null, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NormalizationReplayExecutionResult resume(
            UUID requestId,
            long expectedVersion) {
        if (requestId == null || expectedVersion < 1) {
            return new NormalizationReplayExecutionResult.Invalid(
                    "INVALID_REPLAY_RESUME");
        }
        return execute(requestId, expectedVersion, true);
    }

    private NormalizationReplayExecutionResult execute(
            UUID requestId,
            Long requiredVersion,
            boolean manualResume) {
        if (requestId == null) {
            return new NormalizationReplayExecutionResult.NotFound();
        }
        Optional<NormalizationReplayRequest> currentResult = requestRepository.findById(requestId);
        if (currentResult.isEmpty()) {
            return new NormalizationReplayExecutionResult.NotFound();
        }

        NormalizationReplayRequest current = currentResult.get();
        if (current.status().terminal()) {
            return new NormalizationReplayExecutionResult.AlreadyTerminal(current);
        }
        if (requiredVersion != null && current.version() != requiredVersion) {
            return new NormalizationReplayExecutionResult.NotClaimed(current);
        }
        if (current.status() != NormalizationReplayStatus.PENDING
                && !(manualResume
                        && current.status() == NormalizationReplayStatus.FAILED_RETRYABLE)) {
            return new NormalizationReplayExecutionResult.NotClaimed(current);
        }

        Instant startedAt = clock.instant();
        if (!requestRepository.claim(current.id(), current.version(), startedAt)) {
            NormalizationReplayRequest reloaded = requestRepository.findById(current.id())
                    .orElse(current);
            if (reloaded.status().terminal()) {
                return new NormalizationReplayExecutionResult.AlreadyTerminal(reloaded);
            }
            return new NormalizationReplayExecutionResult.NotClaimed(reloaded);
        }
        NormalizationReplayRequest claimed = requestRepository.findById(current.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Claimed replay request disappeared"));
        if (claimed.status() != NormalizationReplayStatus.RUNNING
                || claimed.version() != current.version() + 1
                || claimed.attemptCount() != current.attemptCount() + 1) {
            throw new IllegalStateException("Replay claim returned an incoherent request state");
        }

        StoredRawSnapshot snapshot;
        try {
            snapshot = snapshotReader.findById(claimed.rawSnapshotId()).orElse(null);
        }
        catch (RuntimeException exception) {
            return failClaimed(
                    claimed,
                    NormalizationReplayAttemptOutcome.FAILED_TERMINAL,
                    claimed.expectedPayloadSha256(),
                    "STORED_SNAPSHOT_INVALID",
                    "The stored snapshot cannot be reconstructed safely",
                    startedAt);
        }
        if (snapshot == null) {
            return failClaimed(
                    claimed,
                    NormalizationReplayAttemptOutcome.FAILED_TERMINAL,
                    claimed.expectedPayloadSha256(),
                    "SNAPSHOT_NOT_FOUND",
                    "The stored snapshot no longer exists",
                    startedAt);
        }

        String actualPayloadSha256 = SnapshotHasher.sha256(snapshot.payload());
        if (!"identity".equals(snapshot.payloadCompression())) {
            return failClaimed(
                    claimed,
                    NormalizationReplayAttemptOutcome.FAILED_TERMINAL,
                    actualPayloadSha256,
                    "UNSUPPORTED_COMPRESSION",
                    "The stored snapshot compression is not supported",
                    startedAt);
        }
        if (!claimed.expectedPayloadSha256().equals(snapshot.payloadSha256())
                || !claimed.expectedPayloadSha256().equals(actualPayloadSha256)) {
            return failClaimed(
                    claimed,
                    NormalizationReplayAttemptOutcome.FAILED_TERMINAL,
                    actualPayloadSha256,
                    "PAYLOAD_HASH_MISMATCH",
                    "The stored snapshot payload does not match its expected SHA-256",
                    startedAt);
        }

        normalizationLock.acquire();
        Set<UUID> applicationsBefore = applicationStore.findApplicationIdsBySnapshotId(
                claimed.rawSnapshotId());
        Set<UUID> eventsBefore = anomalyEventStore.findAnomalyEventIdsBySnapshotId(
                claimed.rawSnapshotId());
        Object normalizationSavepoint = savepoint.create();
        NormalizationResult normalizationResult;
        try {
            normalizationResult = normalizationService.normalizeStoredWithinCurrentTransaction(
                    snapshot.asRawSnapshot());
            if (!claimed.rawSnapshotId().equals(normalizationResult.snapshotId())
                    || !claimed.expectedPayloadSha256().equals(
                            normalizationResult.snapshotSha256())) {
                savepoint.rollback(normalizationSavepoint);
                savepoint.release(normalizationSavepoint);
                return failClaimed(
                        claimed,
                        NormalizationReplayAttemptOutcome.FAILED_TERMINAL,
                        actualPayloadSha256,
                        "SNAPSHOT_IDENTITY_MISMATCH",
                        "Normalization resolved another stored snapshot identity",
                        startedAt);
            }
            savepoint.release(normalizationSavepoint);
        }
        catch (RuntimeException exception) {
            savepoint.rollback(normalizationSavepoint);
            savepoint.release(normalizationSavepoint);
            return failClaimed(
                    claimed,
                    NormalizationReplayAttemptOutcome.FAILED_RETRYABLE,
                    actualPayloadSha256,
                    "NORMALIZATION_FAILED",
                    "Stored snapshot normalization failed and can be resumed",
                    startedAt);
        }

        Instant finishedAt = clock.instant();
        NormalizationReplayAttempt attempt = NormalizationReplayAttempt.completed(
                UUID.randomUUID(),
                claimed.id(),
                claimed.attemptCount(),
                claimed.expectedPayloadSha256(),
                actualPayloadSha256,
                normalizationResult,
                startedAt,
                finishedAt);
        attemptJournal.append(attempt);

        Set<UUID> newApplications = difference(
                applicationStore.findApplicationIdsBySnapshotId(claimed.rawSnapshotId()),
                applicationsBefore);
        Set<UUID> newEvents = difference(
                anomalyEventStore.findAnomalyEventIdsBySnapshotId(claimed.rawSnapshotId()),
                eventsBefore);
        applicationStore.correlate(attempt.id(), newApplications, finishedAt);
        anomalyEventStore.correlate(attempt.id(), newEvents, finishedAt);

        if (!requestRepository.complete(claimed.id(), claimed.version(), finishedAt)) {
            throw new IllegalStateException("Replay completion compare-and-set failed");
        }
        NormalizationReplayRequest completed = requestRepository.findById(claimed.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Completed replay request disappeared"));
        return new NormalizationReplayExecutionResult.Completed(completed, attempt);
    }

    private NormalizationReplayExecutionResult failClaimed(
            NormalizationReplayRequest claimed,
            NormalizationReplayAttemptOutcome outcome,
            String actualPayloadSha256,
            String errorCode,
            String errorMessage,
            Instant startedAt) {
        Instant finishedAt = clock.instant();
        NormalizationReplayAttempt attempt = NormalizationReplayAttempt.failed(
                UUID.randomUUID(),
                claimed.id(),
                claimed.attemptCount(),
                outcome,
                claimed.expectedPayloadSha256(),
                actualPayloadSha256,
                errorCode,
                errorMessage,
                startedAt,
                finishedAt);
        attemptJournal.append(attempt);
        NormalizationReplayStatus failureStatus =
                outcome == NormalizationReplayAttemptOutcome.FAILED_RETRYABLE
                        ? NormalizationReplayStatus.FAILED_RETRYABLE
                        : NormalizationReplayStatus.FAILED_TERMINAL;
        if (!requestRepository.fail(
                claimed.id(),
                claimed.version(),
                failureStatus,
                errorCode,
                errorMessage,
                finishedAt)) {
            throw new IllegalStateException("Replay failure compare-and-set failed");
        }
        NormalizationReplayRequest failed = requestRepository.findById(claimed.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Failed replay request disappeared"));
        return new NormalizationReplayExecutionResult.Failed(failed, attempt);
    }

    private Set<UUID> difference(Set<UUID> after, Set<UUID> before) {
        Set<UUID> difference = new LinkedHashSet<>(after);
        difference.removeAll(before);
        return Set.copyOf(difference);
    }
}
