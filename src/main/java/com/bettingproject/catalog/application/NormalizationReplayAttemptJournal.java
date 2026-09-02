package com.bettingproject.catalog.application;

import java.util.List;
import java.util.UUID;

public interface NormalizationReplayAttemptJournal {

    void append(NormalizationReplayAttempt attempt);

    List<NormalizationReplayAttempt> findByRequestId(UUID requestId);
}
