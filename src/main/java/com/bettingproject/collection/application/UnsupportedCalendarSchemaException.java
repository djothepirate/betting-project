package com.bettingproject.collection.application;

public final class UnsupportedCalendarSchemaException extends IllegalArgumentException {

    private final String schemaVersion;

    public UnsupportedCalendarSchemaException(String schemaVersion) {
        super("Unsupported calendar fixture schema: " + schemaVersion);
        this.schemaVersion = schemaVersion;
    }

    public String schemaVersion() {
        return schemaVersion;
    }
}
