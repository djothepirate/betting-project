package com.bettingproject.collection.application.control;

import java.util.List;
import java.util.UUID;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.selection.DailySelectionPolicy.Candidate;

/** Implemented on the catalog side of the boundary, using current authority and stored derivations. */
public interface DailySelectionCandidates {
    List<Candidate> find(UUID completedCollectionId, ProviderCapability capability, int limit);
}
