package com.bettingproject.catalog.domain;

public enum FixtureApplicationOutcome {
    CREATED(true, false),
    UPDATED(true, false),
    UNCHANGED(true, false),
    STALE(false, true),
    EQUAL_AUTHORITY_TIME_CONFLICT(false, true),
    BLOCKED(false, true),
    REJECTED(false, true),
    INVALID_TRANSITION(false, true),
    CONTROL_DIVERGENCE(false, true),
    UNASSIGNED(false, true);

    private final boolean canonicalFixtureRequired;
    private final boolean reasonCodeRequired;

    FixtureApplicationOutcome(boolean canonicalFixtureRequired, boolean reasonCodeRequired) {
        this.canonicalFixtureRequired = canonicalFixtureRequired;
        this.reasonCodeRequired = reasonCodeRequired;
    }

    public boolean canonicalFixtureRequired() {
        return canonicalFixtureRequired;
    }

    public boolean reasonCodeRequired() {
        return reasonCodeRequired;
    }
}
