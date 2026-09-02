package com.bettingproject.operations.application;

import java.time.Instant;
import java.util.UUID;

public record PendingOutboxMessage(
        UUID id,
        String idempotencyKey,
        UUID aggregateId,
        String destination,
        String payloadJson,
        Instant scheduledAt) {
}
