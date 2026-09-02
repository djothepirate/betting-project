package com.bettingproject.catalog.domain;

public enum FixtureIdentityMatch {
    EXACT_ORDER(true, false),
    INVERTED_ORDER_ALLOWED(true, true),
    NO_MATCH(false, false);

    private final boolean matches;
    private final boolean sourceOrderInverted;

    FixtureIdentityMatch(boolean matches, boolean sourceOrderInverted) {
        this.matches = matches;
        this.sourceOrderInverted = sourceOrderInverted;
    }

    public boolean matches() {
        return matches;
    }

    public boolean sourceOrderInverted() {
        return sourceOrderInverted;
    }
}
