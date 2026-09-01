package com.bettingproject.collection.application;

import java.nio.file.Path;

public record StoredEvidence(
        String callId,
        Path rawPayload,
        Path metadata,
        String sha256,
        int httpStatus,
        long latencyMs) {
}
