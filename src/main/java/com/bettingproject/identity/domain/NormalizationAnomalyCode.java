package com.bettingproject.identity.domain;

public enum NormalizationAnomalyCode {
    MISSING_MAPPING,
    AMBIGUOUS_MAPPING,
    REJECTED_MAPPING,
    MAPPING_CONFLICT,
    INVALID_FIXTURE,
    LEGACY_SCHEMA_NOT_NORMALIZABLE,
    UNSUPPORTED_SCHEMA,
    INVALID_SNAPSHOT
}
