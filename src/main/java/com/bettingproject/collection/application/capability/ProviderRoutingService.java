package com.bettingproject.collection.application.capability;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

import com.bettingproject.collection.domain.capability.CapabilityAuthorityRole;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.application.capability.ProviderRoutingResult.Exclusion;
import com.bettingproject.collection.application.capability.ProviderRoutingResult.ExclusionReason;

public final class ProviderRoutingService {
    private final ProviderCapabilityRegistry registry;

    public ProviderRoutingService(ProviderCapabilityRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public ProviderRoutingResult route(CapabilityRouteKey route) {
        ProviderCapability primary = null;
        var controls = new ArrayList<ProviderCapability>();
        var exclusions = new ArrayList<Exclusion>();
        for (ProviderCapability candidate : registry.candidates(route)) {
            if (!candidate.operational()) {
                ExclusionReason reason = !candidate.enabled() ? ExclusionReason.INACTIVE
                        : switch (candidate.status()) {
                            case PILOT -> ExclusionReason.PILOT;
                            case NON_APPLICABLE -> ExclusionReason.NON_APPLICABLE;
                            case BLOCKED_BY_PLAN -> ExclusionReason.BLOCKED_BY_PLAN;
                            default -> ExclusionReason.UNASSIGNED_AUTHORITY;
                        };
                exclusions.add(new Exclusion(candidate, reason));
            }
            else if (candidate.authorityRole() == CapabilityAuthorityRole.PRIMARY) {
                if (primary != null) {
                    throw new IllegalStateException("registry supplied multiple primaries");
                }
                primary = candidate;
            }
            else {
                controls.add(candidate);
            }
        }
        return new ProviderRoutingResult(
                primary == null ? ProviderRoutingResult.Status.NO_PRIMARY : ProviderRoutingResult.Status.READY,
                Optional.ofNullable(primary), controls, exclusions, registry.registryVersion(),
                registry.documentSha256());
    }
}
