package com.bettingproject.catalog.application;

public enum NormalizationReplayStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED_TERMINAL;
    }

    public boolean claimable() {
        return this == PENDING || this == FAILED_RETRYABLE;
    }
}
