package com.bettingproject.enrichment.domain;

import java.util.Objects;

/** Preserves absent, explicit-null and scalar values (including numeric zero). */
public record ObservedScalar(EnrichmentObservationState state, ObservedScalarType type, String value) {

    public ObservedScalar {
        state = Objects.requireNonNull(state, "state");
        if (state == EnrichmentObservationState.NOT_PRESENT || state == EnrichmentObservationState.NULL_VALUE) {
            if (type != null || value != null) {
                throw new IllegalArgumentException("non-value scalar state cannot carry a value");
            }
        } else if (state == EnrichmentObservationState.AVAILABLE) {
            type = Objects.requireNonNull(type, "type");
            if (value == null || value.length() > 256
                    || value.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("available scalar value is invalid");
            }
        } else {
            throw new IllegalArgumentException("scalar state must be absent, null or available");
        }
    }

    public static ObservedScalar missing() {
        return new ObservedScalar(EnrichmentObservationState.NOT_PRESENT, null, null);
    }

    public static ObservedScalar nullValue() {
        return new ObservedScalar(EnrichmentObservationState.NULL_VALUE, null, null);
    }

    public static ObservedScalar of(ObservedScalarType type, String value) {
        return new ObservedScalar(EnrichmentObservationState.AVAILABLE, type, value);
    }

    public boolean isNumeric() {
        return type == ObservedScalarType.INTEGER || type == ObservedScalarType.DECIMAL;
    }

    public java.math.BigDecimal decimalValue() {
        if (!isNumeric()) {
            throw new IllegalStateException("scalar is not numeric");
        }
        return new java.math.BigDecimal(value);
    }
}
