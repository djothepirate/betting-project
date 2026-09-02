package com.bettingproject.operations.application;

import java.time.Instant;
import java.util.UUID;

public record PendingJob(
        UUID id,
        String jobKey,
        String jobType,
        Instant scheduledAt) {
}
