package com.bettingproject.catalog.application;

import java.util.Objects;

import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;

public final class RegistryCalendarAuthorityPolicy implements CalendarAuthorityPolicy {
    private final ProviderCapabilityRegistry registry;

    public RegistryCalendarAuthorityPolicy(ProviderCapabilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public CalendarAuthorityResolution resolve(CalendarAuthorityKey key) {
        Objects.requireNonNull(key, "key");
        ProviderCapabilityKey capabilityKey;
        try {
            capabilityKey = new ProviderCapabilityKey(key.provider(), key.providerCompetitionId(),
                    key.season(), key.phase(), CapabilityDataType.CALENDAR);
        }
        catch (IllegalArgumentException exception) {
            // Legacy runtime keys may exceed configuration bounds; keep the observation unassigned.
            return new CalendarAuthorityResolution(CalendarAuthorityRole.UNASSIGNED, registry.documentSha256());
        }
        CalendarAuthorityRole role = registry.find(capabilityKey)
                .filter(ProviderCapability::operational)
                .map(capability -> switch (capability.authorityRole()) {
                    case PRIMARY -> CalendarAuthorityRole.PRIMARY;
                    case CONTROL -> CalendarAuthorityRole.CONTROL;
                    case UNASSIGNED -> CalendarAuthorityRole.UNASSIGNED;
                })
                .orElse(CalendarAuthorityRole.UNASSIGNED);
        return new CalendarAuthorityResolution(role, registry.documentSha256());
    }
}
