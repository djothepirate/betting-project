package com.bettingproject.catalog.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.identity.domain.ProviderMappingDecision;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("control-api")
public class DefaultMappingDecisionReplayPlanner implements MappingDecisionReplayPlanner {

    private static final String COMMAND_DIGEST_VERSION = "mapping-decision-replay-request-v1";

    private final StoredRawSnapshotReader snapshotReader;
    private final NormalizationReplayRequestRepository requestRepository;
    private final ControlCommandReceiptStore receiptStore;
    private final NormalizationReplayAfterCommitExecutor afterCommitExecutor;

    public DefaultMappingDecisionReplayPlanner(
            StoredRawSnapshotReader snapshotReader,
            NormalizationReplayRequestRepository requestRepository,
            ControlCommandReceiptStore receiptStore,
            NormalizationReplayAfterCommitExecutor afterCommitExecutor) {
        this.snapshotReader = snapshotReader;
        this.requestRepository = requestRepository;
        this.receiptStore = receiptStore;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    @Override
    public List<UUID> createRequests(
            ProviderMappingDecision decision,
            List<MappingDecisionAnomalyReference> correlatedAnomalies,
            Instant createdAt) {
        Set<UUID> snapshotIds = new LinkedHashSet<>();
        correlatedAnomalies.stream()
                .map(MappingDecisionAnomalyReference::rawSnapshotId)
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(snapshotIds::add);

        List<UUID> requestIds = new ArrayList<>();
        for (UUID snapshotId : snapshotIds) {
            StoredRawSnapshot snapshot = snapshotReader.findById(snapshotId)
                    .orElseThrow(() -> new IllegalStateException(
                            "A correlated anomaly points to a missing raw snapshot"));
            String idempotencyKey = internalKey(decision.id(), snapshot.id());
            String commandSha256 = commandSha256(decision.id(), snapshot.id());
            Optional<ControlCommandReceipt> existing = receiptStore.findByIdempotencyKey(
                    idempotencyKey);
            if (existing.isPresent()) {
                requestIds.add(resolveExisting(existing.get(), commandSha256).id());
                continue;
            }

            UUID requestId = UUID.randomUUID();
            UUID receiptId = UUID.randomUUID();
            ControlCommandReceipt receipt = new ControlCommandReceipt(
                    receiptId,
                    idempotencyKey,
                    ControlCommandType.REPLAY_REQUEST,
                    commandSha256,
                    requestId,
                    createdAt);
            NormalizationReplayRequest request = NormalizationReplayRequest.pending(
                    requestId,
                    receiptId,
                    snapshot.id(),
                    snapshot.payloadSha256(),
                    decision.id(),
                    NormalizationReplayOrigin.MAPPING_DECISION,
                    NormalizationReplaySelectorType.SNAPSHOT_ID,
                    snapshot.id().toString(),
                    createdAt);
            receiptStore.insert(receipt);
            requestRepository.insert(request);
            requestIds.add(request.id());
        }

        afterCommitExecutor.executeAfterCommit(requestIds);
        return List.copyOf(requestIds);
    }

    @Override
    public List<UUID> findRequestIds(UUID decisionId) {
        return requestRepository.findByProviderMappingDecisionId(decisionId).stream()
                .map(NormalizationReplayRequest::id)
                .toList();
    }

    static String internalKey(UUID decisionId, UUID snapshotId) {
        return "mapping-decision:" + decisionId + ":snapshot:" + snapshotId;
    }

    private NormalizationReplayRequest resolveExisting(
            ControlCommandReceipt receipt,
            String commandSha256) {
        if (receipt.commandType() != ControlCommandType.REPLAY_REQUEST
                || !receipt.commandSha256().equals(commandSha256)) {
            throw new IllegalStateException(
                    "A mapping replay idempotency key was reused with different content");
        }
        NormalizationReplayRequest request = requestRepository
                .findByControlCommandReceiptId(receipt.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Mapping replay receipt points to a missing request"));
        if (!request.id().equals(receipt.resultResourceId())) {
            throw new IllegalStateException(
                    "Mapping replay receipt points to another request");
        }
        return request;
    }

    private String commandSha256(UUID decisionId, UUID snapshotId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, COMMAND_DIGEST_VERSION);
            updateDigest(digest, decisionId.toString());
            updateDigest(digest, snapshotId.toString());
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
}
