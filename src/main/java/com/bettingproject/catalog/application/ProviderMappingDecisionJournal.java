package com.bettingproject.catalog.application;

import java.util.Optional;
import java.util.UUID;

import com.bettingproject.identity.domain.ProviderMappingDecision;

public interface ProviderMappingDecisionJournal {

    Optional<ProviderMappingDecision> find(UUID decisionId);

    void append(ProviderMappingDecision decision);
}
