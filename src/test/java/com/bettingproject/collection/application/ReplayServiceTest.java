package com.bettingproject.collection.application;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplayServiceTest {

    private final ReplayService replayService = new ReplayService();

    @Test
    void replayingTheSamePayloadIsDeterministic() {
        byte[] payload = "fixture-payload".getBytes(StandardCharsets.UTF_8);

        ReplayResult<String> first = replayService.replay(payload, this::asText);
        ReplayResult<String> second = replayService.replay(payload, this::asText);

        assertThat(first).isEqualTo(second);
        assertThat(first.snapshotSha256()).hasSize(64);
        assertThat(first.value()).isEqualTo("fixture-payload");
    }

    @Test
    void parserFailureKeepsTheSnapshotHash() {
        byte[] payload = "unsupported-schema".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> replayService.replay(payload, ignored -> {
            throw new IllegalArgumentException("schema mismatch");
        }))
                .isInstanceOf(ReplayFailure.class)
                .satisfies(exception -> assertThat(((ReplayFailure) exception).snapshotSha256()).hasSize(64))
                .hasMessageContaining("sha256=");
    }

    private String asText(byte[] payload) {
        return new String(payload, StandardCharsets.UTF_8);
    }
}
