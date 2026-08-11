package com.bettingproject.identity.application;

import java.util.Optional;

import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;

public interface ProviderMappingRepository {

    Optional<ProviderMapping> find(
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase);

    void save(ProviderMapping mapping);
}
