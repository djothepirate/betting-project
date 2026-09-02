package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record ControlCommandReceipt(
        UUID id,
        String idempotencyKey,
        ControlCommandType commandType,
        String commandSha256,
        UUID resultResourceId,
        Instant createdAt) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ControlCommandReceipt {
        id = Objects.requireNonNull(id, "id");
        idempotencyKey = requireVisibleAscii(idempotencyKey);
        commandType = Objects.requireNonNull(commandType, "commandType");
        if (commandSha256 == null || !SHA_256.matcher(commandSha256).matches()) {
            throw new IllegalArgumentException("commandSha256 must be a lowercase SHA-256");
        }
        resultResourceId = Objects.requireNonNull(resultResourceId, "resultResourceId");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static String requireVisibleAscii(String value) {
        if (value == null || value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException(
                    "idempotencyKey must contain between 1 and 128 characters");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                throw new IllegalArgumentException(
                        "idempotencyKey must contain visible ASCII characters only");
            }
        }
        return value;
    }
}
