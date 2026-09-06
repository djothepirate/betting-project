package com.bettingproject.collection.domain.capability;

import java.util.Objects;

public record CapabilityRouteKey(
        String competitionCode, String season, String phase, CapabilityDataType dataType) {
    public CapabilityRouteKey {
        competitionCode = CapabilityText.exact(competitionCode, "competitionCode", 64);
        season = CapabilityText.exact(season, "season", 64);
        phase = CapabilityText.exact(phase, "phase", 64);
        dataType = Objects.requireNonNull(dataType, "dataType");
    }
}
