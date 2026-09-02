package com.bettingproject.collection.adapter.configuration;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class J7ReceiverPropertiesTest {

    @Test
    void defaultsRemainDisabledAndContractBounded() {
        J7ReceiverProperties properties = new J7ReceiverProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getRetentionDays()).isEqualTo(30);
        assertThat(properties.getMaxRequestBytes()).isEqualTo(5_242_880L);
        assertThat(properties.getMaxAckBytes()).isEqualTo(16_384L);
        assertThat(properties.getClientCertificateSha256Allowlist()).isEmpty();
    }

    @Test
    void certificateAllowlistDoesNotExposeMutableState() {
        ArrayList<String> input = new ArrayList<>(List.of("a".repeat(64)));
        J7ReceiverProperties properties = new J7ReceiverProperties();

        properties.setClientCertificateSha256Allowlist(input);
        input.clear();

        assertThat(properties.getClientCertificateSha256Allowlist())
                .containsExactly("a".repeat(64));
        assertThat(properties.getClientCertificateSha256Allowlist())
                .isUnmodifiable();
    }
}
