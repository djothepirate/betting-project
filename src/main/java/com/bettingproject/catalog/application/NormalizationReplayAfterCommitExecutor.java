package com.bettingproject.catalog.application;

import java.util.Collection;
import java.util.UUID;

public interface NormalizationReplayAfterCommitExecutor {

    void executeAfterCommit(Collection<UUID> requestIds);
}
