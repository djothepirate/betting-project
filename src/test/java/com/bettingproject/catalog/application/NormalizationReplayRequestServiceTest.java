package com.bettingproject.catalog.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationReplayRequestServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T19:30:00Z");
    private static final Clock CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
    private static final UUID SNAPSHOT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000630");
    private static final UUID OTHER_SNAPSHOT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000631");
    private static final String SHA_256 = "d".repeat(64);
    private static final String OTHER_SHA_256 = "e".repeat(64);

    private final RecordingSnapshotReader snapshotReader = new RecordingSnapshotReader();
    private final RecordingRequestRepository requestRepository =
            new RecordingRequestRepository();
    private final RecordingReceiptStore receiptStore = new RecordingReceiptStore();
    private final List<String> lockedKeys = new ArrayList<>();
    private final RecordingAfterCommitExecutor afterCommitExecutor =
            new RecordingAfterCommitExecutor();
    private NormalizationReplayRequestService service;

    @BeforeEach
    void setUp() {
        service = new NormalizationReplayRequestService(
                snapshotReader,
                requestRepository,
                receiptStore,
                lockedKeys::add,
                afterCommitExecutor,
                CLOCK);
    }

    @Test
    void createsAndSchedulesARequestSelectedBySnapshotUuid() {
        StoredRawSnapshot snapshot = snapshot(SNAPSHOT_ID, SHA_256);
        snapshotReader.add(snapshot);

        NormalizationReplayRequestResult result = service.request(new NormalizationReplayCommand(
                new NormalizationReplaySelector.BySnapshotId(SNAPSHOT_ID),
                "replay-by-id-630"));

        assertThat(result).isInstanceOf(NormalizationReplayRequestResult.Created.class);
        NormalizationReplayRequest request =
                ((NormalizationReplayRequestResult.Created) result).request();
        assertThat(request.rawSnapshotId()).isEqualTo(SNAPSHOT_ID);
        assertThat(request.expectedPayloadSha256()).isEqualTo(SHA_256);
        assertThat(request.origin()).isEqualTo(NormalizationReplayOrigin.MANUAL);
        assertThat(request.selectorType())
                .isEqualTo(NormalizationReplaySelectorType.SNAPSHOT_ID);
        assertThat(request.selectorValue()).isEqualTo(SNAPSHOT_ID.toString());
        assertThat(request.status()).isEqualTo(NormalizationReplayStatus.PENDING);
        assertThat(request.createdAt()).isEqualTo(CREATED_AT);
        assertThat(lockedKeys).containsExactly("replay-by-id-630");
        assertThat(requestRepository.requests).containsOnlyKeys(request.id());
        assertThat(receiptStore.receipts).hasSize(1);
        ControlCommandReceipt receipt = receiptStore.receipts.values().iterator().next();
        assertThat(receipt.commandType()).isEqualTo(ControlCommandType.REPLAY_REQUEST);
        assertThat(receipt.commandSha256()).matches("[0-9a-f]{64}");
        assertThat(receipt.resultResourceId()).isEqualTo(request.id());
        assertThat(request.controlCommandReceiptId()).isEqualTo(receipt.id());
        assertThat(afterCommitExecutor.scheduled)
                .containsExactly(List.of(request.id()));
    }

    @Test
    void createsARequestForAUniquePayloadSha256() {
        StoredRawSnapshot snapshot = snapshot(SNAPSHOT_ID, SHA_256);
        snapshotReader.add(snapshot);

        NormalizationReplayRequestResult result = service.request(new NormalizationReplayCommand(
                new NormalizationReplaySelector.ByPayloadSha256(SHA_256),
                "replay-by-hash-630"));

        NormalizationReplayRequest request =
                ((NormalizationReplayRequestResult.Created) result).request();
        assertThat(request.rawSnapshotId()).isEqualTo(SNAPSHOT_ID);
        assertThat(request.selectorType())
                .isEqualTo(NormalizationReplaySelectorType.PAYLOAD_SHA256);
        assertThat(request.selectorValue()).isEqualTo(SHA_256);
        assertThat(afterCommitExecutor.scheduled).containsExactly(List.of(request.id()));
    }

    @Test
    void absentUuidOrPayloadHashCreatesNothing() {
        NormalizationReplayRequestResult byId = service.request(new NormalizationReplayCommand(
                new NormalizationReplaySelector.BySnapshotId(SNAPSHOT_ID),
                "replay-missing-id"));
        NormalizationReplayRequestResult byHash = service.request(new NormalizationReplayCommand(
                new NormalizationReplaySelector.ByPayloadSha256(SHA_256),
                "replay-missing-hash"));

        assertThat(byId).isEqualTo(new NormalizationReplayRequestResult.NotFound("raw_snapshot"));
        assertThat(byHash)
                .isEqualTo(new NormalizationReplayRequestResult.NotFound("raw_snapshot"));
        assertNothingCreated();
        assertThat(lockedKeys).containsExactly("replay-missing-id", "replay-missing-hash");
    }

    @Test
    void ambiguousPayloadHashCreatesNothingAndReturnsAnExplicitResult() {
        snapshotReader.add(snapshot(SNAPSHOT_ID, SHA_256));
        snapshotReader.add(snapshot(OTHER_SNAPSHOT_ID, SHA_256));

        NormalizationReplayRequestResult result = service.request(new NormalizationReplayCommand(
                new NormalizationReplaySelector.ByPayloadSha256(SHA_256),
                "replay-ambiguous-hash"));

        assertThat(result).isEqualTo(
                new NormalizationReplayRequestResult.AmbiguousPayloadSha256(SHA_256));
        assertNothingCreated();
    }

    @Test
    void identicalIdempotentRequestReturnsTheOriginalAndReschedulesPendingWork() {
        snapshotReader.add(snapshot(SNAPSHOT_ID, SHA_256));
        NormalizationReplayCommand command = new NormalizationReplayCommand(
                new NormalizationReplaySelector.BySnapshotId(SNAPSHOT_ID),
                "replay-idempotent");

        NormalizationReplayRequestResult.Created first =
                (NormalizationReplayRequestResult.Created) service.request(command);
        NormalizationReplayRequestResult second = service.request(command);

        assertThat(second).isInstanceOf(NormalizationReplayRequestResult.AlreadyCreated.class);
        assertThat(((NormalizationReplayRequestResult.AlreadyCreated) second).request())
                .isEqualTo(first.request());
        assertThat(requestRepository.requests).hasSize(1);
        assertThat(receiptStore.receipts).hasSize(1);
        assertThat(afterCommitExecutor.scheduled)
                .containsExactly(List.of(first.request().id()), List.of(first.request().id()));
        assertThat(lockedKeys).containsExactly("replay-idempotent", "replay-idempotent");
    }

    @Test
    void reusedIdempotencyKeyWithAnotherSelectorConflictsWithoutNewWrites() {
        snapshotReader.add(snapshot(SNAPSHOT_ID, SHA_256));
        snapshotReader.add(snapshot(OTHER_SNAPSHOT_ID, OTHER_SHA_256));
        NormalizationReplayRequestResult.Created first =
                (NormalizationReplayRequestResult.Created) service.request(
                        new NormalizationReplayCommand(
                                new NormalizationReplaySelector.BySnapshotId(SNAPSHOT_ID),
                                "replay-conflicting-key"));

        NormalizationReplayRequestResult result = service.request(
                new NormalizationReplayCommand(
                        new NormalizationReplaySelector.BySnapshotId(OTHER_SNAPSHOT_ID),
                        "replay-conflicting-key"));

        assertThat(result).isInstanceOf(
                NormalizationReplayRequestResult.IdempotencyConflict.class);
        assertThat(requestRepository.requests).containsOnlyKeys(first.request().id());
        assertThat(receiptStore.receipts).hasSize(1);
        assertThat(afterCommitExecutor.scheduled)
                .containsExactly(List.of(first.request().id()));
    }

    @Test
    void invalidCommandDoesNotLockOrPersistOrSchedule() {
        NormalizationReplayRequestResult missingCommand = service.request(null);
        NormalizationReplayRequestResult missingSelector = service.request(
                new NormalizationReplayCommand(null, "replay-missing-selector"));
        NormalizationReplayRequestResult invalidKey = service.request(
                new NormalizationReplayCommand(
                        new NormalizationReplaySelector.BySnapshotId(SNAPSHOT_ID),
                        "contains space"));

        assertThat(missingCommand)
                .isEqualTo(new NormalizationReplayRequestResult.Invalid(
                        "INVALID_REPLAY_REQUEST"));
        assertThat(missingSelector)
                .isEqualTo(new NormalizationReplayRequestResult.Invalid(
                        "INVALID_REPLAY_REQUEST"));
        assertThat(invalidKey)
                .isEqualTo(new NormalizationReplayRequestResult.Invalid(
                        "INVALID_REPLAY_REQUEST"));
        assertThat(lockedKeys).isEmpty();
        assertNothingCreated();
    }

    private StoredRawSnapshot snapshot(UUID id, String sha256) {
        return new StoredRawSnapshot(
                id,
                "synthetic-provider",
                "/calendar",
                CREATED_AT.minusSeconds(60),
                sha256,
                "identity",
                "{}".getBytes(StandardCharsets.UTF_8),
                "test-connector-v1");
    }

    private void assertNothingCreated() {
        assertThat(requestRepository.requests).isEmpty();
        assertThat(receiptStore.receipts).isEmpty();
        assertThat(afterCommitExecutor.scheduled).isEmpty();
    }

    private static final class RecordingSnapshotReader implements StoredRawSnapshotReader {

        private final Map<UUID, StoredRawSnapshot> snapshots = new LinkedHashMap<>();

        private void add(StoredRawSnapshot snapshot) {
            snapshots.put(snapshot.id(), snapshot);
        }

        @Override
        public Optional<StoredRawSnapshot> findById(UUID snapshotId) {
            return Optional.ofNullable(snapshots.get(snapshotId));
        }

        @Override
        public List<StoredRawSnapshot> findByPayloadSha256(String payloadSha256) {
            return snapshots.values().stream()
                    .filter(snapshot -> snapshot.payloadSha256().equals(payloadSha256))
                    .toList();
        }
    }

    private static final class RecordingRequestRepository
            implements NormalizationReplayRequestRepository {

        private final Map<UUID, NormalizationReplayRequest> requests = new LinkedHashMap<>();

        @Override
        public Optional<NormalizationReplayRequest> findById(UUID requestId) {
            return Optional.ofNullable(requests.get(requestId));
        }

        @Override
        public Optional<NormalizationReplayRequest> findByControlCommandReceiptId(
                UUID receiptId) {
            return requests.values().stream()
                    .filter(request -> request.controlCommandReceiptId().equals(receiptId))
                    .findFirst();
        }

        @Override
        public List<NormalizationReplayRequest> findByProviderMappingDecisionId(
                UUID decisionId) {
            return requests.values().stream()
                    .filter(request -> decisionId.equals(request.providerMappingDecisionId()))
                    .toList();
        }

        @Override
        public void insert(NormalizationReplayRequest request) {
            requests.put(request.id(), request);
        }

        @Override
        public boolean claim(UUID requestId, long expectedVersion, Instant claimedAt) {
            throw unsupported();
        }

        @Override
        public boolean complete(UUID requestId, long expectedVersion, Instant completedAt) {
            throw unsupported();
        }

        @Override
        public boolean fail(
                UUID requestId,
                long expectedVersion,
                NormalizationReplayStatus failureStatus,
                String errorCode,
                String errorMessage,
                Instant failedAt) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used by request service unit tests");
        }
    }

    private static final class RecordingReceiptStore implements ControlCommandReceiptStore {

        private final Map<String, ControlCommandReceipt> receipts = new HashMap<>();

        @Override
        public Optional<ControlCommandReceipt> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(receipts.get(idempotencyKey));
        }

        @Override
        public void insert(ControlCommandReceipt receipt) {
            receipts.put(receipt.idempotencyKey(), receipt);
        }
    }

    private static final class RecordingAfterCommitExecutor
            implements NormalizationReplayAfterCommitExecutor {

        private final List<List<UUID>> scheduled = new ArrayList<>();

        @Override
        public void executeAfterCommit(Collection<UUID> requestIds) {
            scheduled.add(List.copyOf(requestIds));
        }
    }
}
