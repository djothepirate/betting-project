package com.bettingproject.collection.application.capability;

import java.util.List;
import java.util.Optional;

import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;

public interface ProviderCapabilityRegistry {
    String registryVersion();

    String documentSha256();

    Optional<ProviderCapability> find(ProviderCapabilityKey key);

    List<ProviderCapability> candidates(CapabilityRouteKey route);
}
