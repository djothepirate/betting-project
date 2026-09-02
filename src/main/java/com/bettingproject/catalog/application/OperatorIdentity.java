package com.bettingproject.catalog.application;

import java.util.Optional;

public record OperatorIdentity(String value) {

    public OperatorIdentity {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("operator identity must not be blank");
        }
        value = value.trim();
        if (value.length() > 100) {
            throw new IllegalArgumentException(
                    "operator identity must contain at most 100 characters");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "operator identity must not contain control characters");
        }
    }

    public static Optional<OperatorIdentity> fromConfiguredValue(String value) {
        try {
            return value == null || value.isBlank()
                    ? Optional.empty()
                    : Optional.of(new OperatorIdentity(value));
        }
        catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
