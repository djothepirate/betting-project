package com.bettingproject.catalog.application;

public enum NormalizationReplayAttemptOutcome {
    COMPLETED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL
}
