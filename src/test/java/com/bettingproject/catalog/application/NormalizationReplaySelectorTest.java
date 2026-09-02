package com.bettingproject.catalog.application;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizationReplaySelectorTest {

    private static final String SHA_256 = "a".repeat(64);

    @Test
    void snapshotSelectorKeepsItsUuidAsCanonicalValue() {
        UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-000000000601");

        NormalizationReplaySelector.BySnapshotId selector =
                new NormalizationReplaySelector.BySnapshotId(snapshotId);

        assertThat(selector.snapshotId()).isEqualTo(snapshotId);
        assertThat(selector.canonicalValue()).isEqualTo(snapshotId.toString());
    }

    @Test
    void payloadSelectorKeepsAnExactLowercaseSha256() {
        NormalizationReplaySelector.ByPayloadSha256 selector =
                new NormalizationReplaySelector.ByPayloadSha256(SHA_256);

        assertThat(selector.payloadSha256()).isEqualTo(SHA_256);
        assertThat(selector.canonicalValue()).isEqualTo(SHA_256);
    }

    @Test
    void selectorsRejectMissingOrNonCanonicalValues() {
        assertThatThrownBy(() -> new NormalizationReplaySelector.BySnapshotId(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("snapshotId");
        assertThatThrownBy(() -> new NormalizationReplaySelector.ByPayloadSha256(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase SHA-256");
        assertThatThrownBy(() -> new NormalizationReplaySelector.ByPayloadSha256(
                "A".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase SHA-256");
        assertThatThrownBy(() -> new NormalizationReplaySelector.ByPayloadSha256(
                "a".repeat(63)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lowercase SHA-256");
    }
}
