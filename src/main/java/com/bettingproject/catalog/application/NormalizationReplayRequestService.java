package com.bettingproject.catalog.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("control-api")
public class NormalizationReplayRequestService {

    private static final String COMMAND_DIGEST_VERSION = "stored-snapshot-replay-request-v1";

    private final StoredRawSnapshotReader snapshotReader;
    private final NormalizationReplayRequestRepository requestRepository;
    private final ControlCommandReceiptStore receiptStore;
    private final ControlCommandLock controlCommandLock;
    private final NormalizationReplayAfterCommitExecutor afterCommitExecutor;
    private final Clock clock;

    public NormalizationReplayRequestService(
            StoredRawSnapshotReader snapshotReader,
            NormalizationReplayRequestRepository requestRepository,
            ControlCommandReceiptStore receiptStore,
            ControlCommandLock controlCommandLock,
            NormalizationReplayAfterCommitExecutor afterCommitExecutor,
            Clock clock) {
        this.snapshotReader = snapshotReader;
        this.requestRepository = requestRepository;
        this.receiptStore = receiptStore;
        this.controlCommandLock = controlCommandLock;
        this.afterCommitExecutor = afterCommitExecutor;
        this.clock = clock;
    }

    @Transactional
    public NormalizationReplayRequestResult request(NormalizationReplayCommand command) {
        ValidatedCommand validated = validate(command);
        if (validated == null) {
            return new NormalizationReplayRequestResult.Invalid("INVALID_REPLAY_REQUEST");
        }

        controlCommandLock.acquire(validated.idempotencyKey());
        Optional<ControlCommandReceipt> existingReceipt = receiptStore.findByIdempotencyKey(
                validated.idempotencyKey());
        if (existingReceipt.isPresent()) {
            return resolveIdempotent(existingReceipt.get(), validated.commandSha256());
        }

        SnapshotResolution resolution = resolveSnapshot(validated.selector());
        if (resolution.notFound()) {
            return new NormalizationReplayRequestResult.NotFound("raw_snapshot");
        }
        if (resolution.ambiguous()) {
            return new NormalizationReplayRequestResult.AmbiguousPayloadSha256(
                    validated.selector().canonicalValue());
        }

        StoredRawSnapshot snapshot = resolution.snapshot();
        Instant createdAt = clock.instant();
        UUID requestId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        ControlCommandReceipt receipt = new ControlCommandReceipt(
                receiptId,
                validated.idempotencyKey(),
                ControlCommandType.REPLAY_REQUEST,
                validated.commandSha256(),
                requestId,
                createdAt);
        NormalizationReplayRequest request = NormalizationReplayRequest.pending(
                requestId,
                receiptId,
                snapshot.id(),
                snapshot.payloadSha256(),
                null,
                NormalizationReplayOrigin.MANUAL,
                selectorType(validated.selector()),
                validated.selector().canonicalValue(),
                createdAt);

        receiptStore.insert(receipt);
        requestRepository.insert(request);
        afterCommitExecutor.executeAfterCommit(List.of(request.id()));
        return new NormalizationReplayRequestResult.Created(request);
    }

    private NormalizationReplayRequestResult resolveIdempotent(
            ControlCommandReceipt receipt,
            String commandSha256) {
        if (receipt.commandType() != ControlCommandType.REPLAY_REQUEST
                || !receipt.commandSha256().equals(commandSha256)) {
            return new NormalizationReplayRequestResult.IdempotencyConflict();
        }
        NormalizationReplayRequest request = requestRepository
                .findByControlCommandReceiptId(receipt.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Control command receipt points to a missing replay request"));
        if (!request.id().equals(receipt.resultResourceId())) {
            throw new IllegalStateException(
                    "Control command receipt points to another replay request");
        }
        if (request.status() == NormalizationReplayStatus.PENDING) {
            afterCommitExecutor.executeAfterCommit(List.of(request.id()));
        }
        return new NormalizationReplayRequestResult.AlreadyCreated(request);
    }

    private SnapshotResolution resolveSnapshot(NormalizationReplaySelector selector) {
        if (selector instanceof NormalizationReplaySelector.BySnapshotId byId) {
            return snapshotReader.findById(byId.snapshotId())
                    .map(SnapshotResolution::found)
                    .orElseGet(SnapshotResolution::missing);
        }
        NormalizationReplaySelector.ByPayloadSha256 bySha256 =
                (NormalizationReplaySelector.ByPayloadSha256) selector;
        List<StoredRawSnapshot> matches = snapshotReader.findByPayloadSha256(
                bySha256.payloadSha256());
        if (matches.isEmpty()) {
            return SnapshotResolution.missing();
        }
        if (matches.size() > 1) {
            return SnapshotResolution.ambiguousResult();
        }
        return SnapshotResolution.found(matches.getFirst());
    }

    private ValidatedCommand validate(NormalizationReplayCommand command) {
        if (command == null || command.selector() == null) {
            return null;
        }
        String idempotencyKey;
        try {
            idempotencyKey = ControlCommandReceipt.requireVisibleAscii(command.idempotencyKey());
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
        return new ValidatedCommand(
                command.selector(),
                idempotencyKey,
                commandSha256(command.selector()));
    }

    private NormalizationReplaySelectorType selectorType(
            NormalizationReplaySelector selector) {
        return selector instanceof NormalizationReplaySelector.BySnapshotId
                ? NormalizationReplaySelectorType.SNAPSHOT_ID
                : NormalizationReplaySelectorType.PAYLOAD_SHA256;
    }

    private String commandSha256(NormalizationReplaySelector selector) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, COMMAND_DIGEST_VERSION);
            updateDigest(digest, selectorType(selector).name());
            updateDigest(digest, selector.canonicalValue());
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private record ValidatedCommand(
            NormalizationReplaySelector selector,
            String idempotencyKey,
            String commandSha256) {
    }

    private record SnapshotResolution(
            StoredRawSnapshot snapshot,
            boolean notFound,
            boolean ambiguous) {

        static SnapshotResolution found(StoredRawSnapshot snapshot) {
            return new SnapshotResolution(snapshot, false, false);
        }

        static SnapshotResolution missing() {
            return new SnapshotResolution(null, true, false);
        }

        static SnapshotResolution ambiguousResult() {
            return new SnapshotResolution(null, false, true);
        }
    }
}
