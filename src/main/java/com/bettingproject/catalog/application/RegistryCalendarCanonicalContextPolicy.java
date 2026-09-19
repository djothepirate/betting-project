package com.bettingproject.catalog.application;

import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import java.util.Objects;
import java.util.Optional;

/** Projects only a proved, exact operational assignment; no season/phase guessing or sibling lookup. */
public final class RegistryCalendarCanonicalContextPolicy implements CalendarCanonicalContextPolicy {
    private final ProviderCapabilityRegistry registry;

    public RegistryCalendarCanonicalContextPolicy(ProviderCapabilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public Optional<CalendarCanonicalContext> resolve(CalendarAuthorityKey sourceKey) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        ProviderCapabilityKey key;
        try {
            key = new ProviderCapabilityKey(sourceKey.provider(), sourceKey.providerCompetitionId(),
                    sourceKey.season(), sourceKey.phase(), CapabilityDataType.CALENDAR);
        } catch (IllegalArgumentException invalidConfigurationBound) {
            // Runtime literals outside configuration bounds cannot inherit a neighboring assignment.
            return Optional.empty();
        }
        return registry.find(key).filter(ProviderCapability::operational)
                .map(capability -> new CalendarCanonicalContext(
                        capability.route().season(), capability.route().phase()));
    }
}
