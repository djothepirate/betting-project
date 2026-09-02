package com.bettingproject.collection.application;

public final class CalendarSnapshotSchemas {

    public static final String LEGACY_V1 = "cal01-fixture-v1";
    public static final String CANONICAL_V2 = "cal01-fixture-v2";
    public static final String CANONICAL_V3 = "cal01-fixture-v3";

    private CalendarSnapshotSchemas() {
    }

    public static boolean isNormalizable(String schemaVersion) {
        return CANONICAL_V2.equals(schemaVersion) || CANONICAL_V3.equals(schemaVersion);
    }
}
