package com.bettingproject.collection.domain.capability;

import java.util.List;
import java.util.Objects;

public record ProviderCapability(
        ProviderCapabilityKey key, CapabilityRouteKey route, CapabilityStatus status,
        CapabilityAuthorityRole authorityRole, boolean enabled,
        List<CapabilityEvidenceReference> evidence) {
    public ProviderCapability {
        key = Objects.requireNonNull(key, "key");
        route = Objects.requireNonNull(route, "route");
        status = Objects.requireNonNull(status, "status");
        authorityRole = Objects.requireNonNull(authorityRole, "authorityRole");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        for (String value : List.of(key.provider(), key.providerCompetitionId(), key.sourceSeason(),
                key.sourcePhase(), route.competitionCode(), route.season(), route.phase())) {
            CapabilityText.assignment(value);
        }
        if (key.dataType() != route.dataType()) {
            throw new IllegalArgumentException("capability and route data types differ");
        }
        boolean coherent = switch (status) {
            case PRIMARY -> authorityRole == CapabilityAuthorityRole.PRIMARY;
            case CONTROL -> authorityRole == CapabilityAuthorityRole.CONTROL;
            case CALENDAR_ONLY -> key.dataType() == CapabilityDataType.CALENDAR;
            case PILOT, NON_APPLICABLE, BLOCKED_BY_PLAN ->
                    authorityRole == CapabilityAuthorityRole.UNASSIGNED;
        };
        if (!coherent) {
            throw new IllegalArgumentException("inconsistent capability coverage and authority");
        }
        if (enabled && evidence.isEmpty()) {
            throw new IllegalArgumentException("enabled capability requires evidence references");
        }
    }

    public boolean operational() {
        return enabled && authorityRole != CapabilityAuthorityRole.UNASSIGNED
                && (status == CapabilityStatus.PRIMARY || status == CapabilityStatus.CONTROL
                    || status == CapabilityStatus.CALENDAR_ONLY);
    }
}
