package com.bettingproject.catalog.application;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public sealed interface NormalizationReplaySelector
        permits NormalizationReplaySelector.BySnapshotId,
                NormalizationReplaySelector.ByPayloadSha256 {

    Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    String canonicalValue();

    record BySnapshotId(UUID snapshotId) implements NormalizationReplaySelector {

        public BySnapshotId {
            snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        }

        @Override
        public String canonicalValue() {
            return snapshotId.toString();
        }
    }

    record ByPayloadSha256(String payloadSha256) implements NormalizationReplaySelector {

        public ByPayloadSha256 {
            if (payloadSha256 == null || !SHA_256.matcher(payloadSha256).matches()) {
                throw new IllegalArgumentException(
                        "payloadSha256 must be a lowercase SHA-256");
            }
        }

        @Override
        public String canonicalValue() {
            return payloadSha256;
        }
    }
}
