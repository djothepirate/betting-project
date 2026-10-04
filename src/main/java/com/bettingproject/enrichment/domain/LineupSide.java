package com.bettingproject.enrichment.domain;

import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Provider starter IDs and explicitly available identity evidence for one side. */
public record LineupSide(boolean fieldPresent, List<String> starterProviderIds,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<LineupPlayerEvidence> starterEvidence) {
    public LineupSide {
        starterProviderIds = List.copyOf(Objects.requireNonNull(starterProviderIds, "starterProviderIds"));
        starterEvidence = List.copyOf(Objects.requireNonNull(starterEvidence, "starterEvidence"));
        if (!fieldPresent && !starterProviderIds.isEmpty()) {
            throw new IllegalArgumentException("starter IDs cannot exist when the field is absent");
        }
        if (!fieldPresent && !starterEvidence.isEmpty()) {
            throw new IllegalArgumentException("starter evidence cannot exist when the field is absent");
        }
        if (!starterEvidence.isEmpty()) {
            if (starterEvidence.size() != starterProviderIds.size()) {
                throw new IllegalArgumentException("starter evidence must preserve each source player reference and order");
            }
            for (int index = 0; index < starterEvidence.size(); index++) {
                if (!starterEvidence.get(index).providerPlayerId().equals(starterProviderIds.get(index))) {
                    throw new IllegalArgumentException("starter evidence must preserve each source player reference and order");
                }
            }
        }
    }

    /** Compatibility constructor for v1 replay representations, which contained IDs only. */
    public LineupSide(boolean fieldPresent, List<String> starterProviderIds) {
        this(fieldPresent, starterProviderIds, List.of());
    }
}
