package com.bettingproject.catalog.application;

public record NormalizationReplayCommand(
        NormalizationReplaySelector selector,
        String idempotencyKey) {
}
