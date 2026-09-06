package com.bettingproject.collection.application.capability;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.bettingproject.collection.domain.capability.ProviderCapability;

public record ProviderRoutingResult(
        Status status, Optional<ProviderCapability> primary, List<ProviderCapability> controls,
        List<Exclusion> exclusions, String registryVersion, String documentSha256) {
    public enum Status { READY, NO_PRIMARY }

    public enum ExclusionReason { INACTIVE, PILOT, NON_APPLICABLE, BLOCKED_BY_PLAN, UNASSIGNED_AUTHORITY }

    public record Exclusion(ProviderCapability capability, ExclusionReason reason) {
        public Exclusion {
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public ProviderRoutingResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(primary, "primary");
        controls = List.copyOf(controls);
        exclusions = List.copyOf(exclusions);
        if ((status == Status.READY) != primary.isPresent()) {
            throw new IllegalArgumentException("route status does not match primary presence");
        }
        Objects.requireNonNull(registryVersion, "registryVersion");
        Objects.requireNonNull(documentSha256, "documentSha256");
    }
}
