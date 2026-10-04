package com.bettingproject.qualification.domain;

import java.math.BigDecimal;

/** Evidence required before comparing a player identity across endpoint families. */
public record PlayerEndpointIdentityEvidence(
        String endpoint,
        String providerTeamId,
        String providerPlayerId,
        String sourceName,
        String role,
        BigDecimal minutesPlayed) {

    public PlayerEndpointIdentityEvidence {
        endpoint = require(endpoint, "endpoint");
        providerTeamId = require(providerTeamId, "providerTeamId");
        providerPlayerId = require(providerPlayerId, "providerPlayerId");
        sourceName = require(sourceName, "sourceName");
        role = optional(role, "role");
        if (minutesPlayed != null && minutesPlayed.signum() < 0) {
            throw new IllegalArgumentException("minutesPlayed must not be negative");
        }
    }

    boolean sufficientForCrossEndpointMatch() {
        return role != null;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 256
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String optional(String value, String name) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.length() > 128
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
