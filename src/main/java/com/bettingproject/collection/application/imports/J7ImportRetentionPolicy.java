package com.bettingproject.collection.application.imports;

import java.time.Duration;
import java.util.Objects;

public record J7ImportRetentionPolicy(Duration payloadRetention) {

    private static final Duration MAXIMUM_RETENTION = Duration.ofDays(3_650);

    public J7ImportRetentionPolicy {
        payloadRetention = Objects.requireNonNull(payloadRetention, "payloadRetention");
        if (payloadRetention.isZero()
                || payloadRetention.isNegative()
                || payloadRetention.compareTo(MAXIMUM_RETENTION) > 0
                || payloadRetention.toDays() < 1
                || !Duration.ofDays(payloadRetention.toDays()).equals(payloadRetention)) {
            throw new IllegalArgumentException(
                    "payloadRetention must contain between 1 and 3650 whole days");
        }
    }
}
