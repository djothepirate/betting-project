package com.bettingproject.qualification.domain;

import java.util.Objects;

/** A deterministic, not-yet-persisted player-quality finding produced from parsed evidence. */
public record PlayerQualityConcern(
        QualityIssueCode code,
        String providerTeamId,
        String providerEntityId,
        String relatedProviderEntityId) {

    public PlayerQualityConcern {
        code = Objects.requireNonNull(code, "code");
        if (code != QualityIssueCode.MISSING_PLAYER_YELLOW_CARD
                && code != QualityIssueCode.CROSS_ENDPOINT_PLAYER_ID_MISMATCH) {
            throw new IllegalArgumentException("concern code is not a cross-endpoint player finding");
        }
        providerTeamId = require(providerTeamId, "providerTeamId");
        providerEntityId = require(providerEntityId, "providerEntityId");
        if (relatedProviderEntityId != null) {
            relatedProviderEntityId = require(relatedProviderEntityId, "relatedProviderEntityId");
        }
        if ((code == QualityIssueCode.CROSS_ENDPOINT_PLAYER_ID_MISMATCH)
                != (relatedProviderEntityId != null)) {
            throw new IllegalArgumentException("related player reference must match finding type");
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 200
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
