package com.bettingproject.identity.domain;

import java.time.Instant;
import java.util.UUID;

import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizationAnomalyEventTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void observedSupportsTheLegacyIgnoredStateWithoutOpeningIt() {
        NormalizationAnomalyEvent event = event(
                NormalizationAnomalyEventType.OBSERVED,
                AnomalyStatus.IGNORED,
                AnomalyStatus.IGNORED);

        assertThat(event.fixtureApplicationLogId()).isNotNull();
        assertThat(event.details()).isEqualTo("details");
    }

    @Test
    void rejectsAnInvalidReopenTransition() {
        assertThatThrownBy(() -> event(
                NormalizationAnomalyEventType.REOPENED,
                AnomalyStatus.OPEN,
                AnomalyStatus.OPEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid anomaly event transition");
    }

    @Test
    void acceptsTheFourLifecycleTransitions() {
        assertThat(event(NormalizationAnomalyEventType.OPENED, null, AnomalyStatus.OPEN))
                .isNotNull();
        assertThat(event(
                NormalizationAnomalyEventType.OBSERVED,
                AnomalyStatus.OPEN,
                AnomalyStatus.OPEN)).isNotNull();
        assertThat(event(
                NormalizationAnomalyEventType.RESOLVED,
                AnomalyStatus.OPEN,
                AnomalyStatus.RESOLVED)).isNotNull();
        assertThat(event(
                NormalizationAnomalyEventType.REOPENED,
                AnomalyStatus.RESOLVED,
                AnomalyStatus.OPEN)).isNotNull();
    }

    private NormalizationAnomalyEvent event(
            NormalizationAnomalyEventType type,
            AnomalyStatus previous,
            AnomalyStatus resulting) {
        return new NormalizationAnomalyEvent(
                UUID.randomUUID(), UUID.randomUUID(), type, previous, resulting,
                UUID.randomUUID(), " details ", NOW);
    }
}
