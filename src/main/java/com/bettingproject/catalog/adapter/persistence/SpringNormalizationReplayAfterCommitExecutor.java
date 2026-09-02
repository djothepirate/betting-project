package com.bettingproject.catalog.adapter.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.bettingproject.catalog.application.NormalizationReplayAfterCommitExecutor;
import com.bettingproject.catalog.application.NormalizationReplayExecutionService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Profile("control-api")
public class SpringNormalizationReplayAfterCommitExecutor
        implements NormalizationReplayAfterCommitExecutor {

    private final NormalizationReplayExecutionService executionService;

    public SpringNormalizationReplayAfterCommitExecutor(
            NormalizationReplayExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public void executeAfterCommit(Collection<UUID> requestIds) {
        List<UUID> requests = List.copyOf(requestIds);
        if (requests.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Replay execution can only be scheduled from an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (UUID requestId : requests) {
                            try {
                                executionService.executeImmediately(requestId);
                            }
                            catch (RuntimeException ignored) {
                                // The durable non-terminal request remains available for manual resume.
                            }
                        }
                    }
                });
    }
}
