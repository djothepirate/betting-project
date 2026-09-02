package com.bettingproject.identity.application;

import java.util.Optional;

import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import com.bettingproject.identity.domain.ProviderMappingKey;

public interface ProviderMappingRepository {

    Optional<ProviderMapping> find(ProviderMappingKey key);

    default Optional<ProviderMapping> find(
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase) {
        return find(new ProviderMappingKey(
                provider, entityType, providerEntityId, season, phase));
    }

    StoredProviderMapping insertIfAbsentAndResolve(ProviderMapping mapping);

    Optional<ProviderMapping> findForUpdate(ProviderMappingKey key);

    StoredProviderMapping insertForDecisionIfAbsent(ProviderMapping mapping);

    boolean updateIfVersion(ProviderMapping mapping, long expectedVersion);
}
