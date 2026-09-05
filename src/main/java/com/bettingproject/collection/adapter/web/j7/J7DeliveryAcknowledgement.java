package com.bettingproject.collection.adapter.web.j7;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record J7DeliveryAcknowledgement(
        String protocolVersion,
        UUID remoteImportId,
        Status status,
        UUID exportId,
        String fileSha256,
        String dataSha256,
        Instant receivedAt) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public J7DeliveryAcknowledgement {
        if (!J7ImportHttpContract.PROTOCOL_VERSION.equals(protocolVersion)) {
            throw new IllegalArgumentException("protocolVersion must match the J7 contract");
        }
        remoteImportId = Objects.requireNonNull(remoteImportId, "remoteImportId");
        status = Objects.requireNonNull(status, "status");
        exportId = Objects.requireNonNull(exportId, "exportId");
        requireSha256(fileSha256, "fileSha256");
        requireSha256(dataSha256, "dataSha256");
        receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
    }

    private static void requireSha256(String value, String name) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256");
        }
    }

    public enum Status {
        IMPORTED,
        DUPLICATE
    }
}
