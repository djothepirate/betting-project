package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NormalizationReplayRequestRepository {

    Optional<NormalizationReplayRequest> findById(UUID requestId);

    Optional<NormalizationReplayRequest> findByControlCommandReceiptId(UUID receiptId);

    List<NormalizationReplayRequest> findByProviderMappingDecisionId(UUID decisionId);

    void insert(NormalizationReplayRequest request);

    boolean claim(UUID requestId, long expectedVersion, Instant claimedAt);

    boolean complete(UUID requestId, long expectedVersion, Instant completedAt);

    boolean fail(
            UUID requestId,
            long expectedVersion,
            NormalizationReplayStatus failureStatus,
            String errorCode,
            String errorMessage,
            Instant failedAt);
}
