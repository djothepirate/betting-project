package com.bettingproject.identity.domain;

import java.time.Instant;
import java.util.UUID;

import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizationAnomalyTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-09-01T11:00:00Z");

    @Test
    void opensWithAStableExactContextAndInitialLifecycleValues() {
        NormalizationAnomaly anomaly = NormalizationAnomaly.open(
                key(), " missing exact mapping ", CREATED_AT);

        assertThat(anomaly.key()).isEqualTo(key());
        assertThat(anomaly.details()).isEqualTo("missing exact mapping");
        assertThat(anomaly.status()).isEqualTo(AnomalyStatus.OPEN);
        assertThat(anomaly.version()).isEqualTo(1);
        assertThat(anomaly.occurrenceCount()).isEqualTo(1);
        assertThat(anomaly.createdAt()).isEqualTo(CREATED_AT);
        assertThat(anomaly.lastSeenAt()).isEqualTo(CREATED_AT);
        assertThat(anomaly.updatedAt()).isEqualTo(CREATED_AT);
        assertThat(anomaly.resolvedAt()).isNull();
    }

    @Test
    void observesAnOpenAnomalyWithoutReplacingItsIdentityOrCreationTime() {
        NormalizationAnomaly initial = NormalizationAnomaly.open(key(), "first", CREATED_AT);

        NormalizationAnomaly observed = initial.observe("second", LATER);

        assertThat(observed.id()).isEqualTo(initial.id());
        assertThat(observed.key()).isEqualTo(initial.key());
        assertThat(observed.createdAt()).isEqualTo(initial.createdAt());
        assertThat(observed.details()).isEqualTo("second");
        assertThat(observed.status()).isEqualTo(AnomalyStatus.OPEN);
        assertThat(observed.version()).isEqualTo(2);
        assertThat(observed.occurrenceCount()).isEqualTo(2);
        assertThat(observed.lastSeenAt()).isEqualTo(LATER);
    }

    @Test
    void resolutionDoesNotCountAsANewOccurrenceAndLaterObservationReopens() {
        NormalizationAnomaly initial = NormalizationAnomaly.open(key(), "first", CREATED_AT);
        NormalizationAnomaly resolved = initial.resolve(LATER);
        Instant reopenedAt = LATER.plusSeconds(60);

        NormalizationAnomaly reopened = resolved.observe("again", reopenedAt);

        assertThat(resolved.status()).isEqualTo(AnomalyStatus.RESOLVED);
        assertThat(resolved.resolvedAt()).isEqualTo(LATER);
        assertThat(resolved.occurrenceCount()).isEqualTo(1);
        assertThat(reopened.status()).isEqualTo(AnomalyStatus.OPEN);
        assertThat(reopened.resolvedAt()).isNull();
        assertThat(reopened.version()).isEqualTo(3);
        assertThat(reopened.occurrenceCount()).isEqualTo(2);
    }

    @Test
    void onlyOpenAnomaliesCanBeResolved() {
        NormalizationAnomaly resolved = NormalizationAnomaly.open(
                key(), "first", CREATED_AT).resolve(LATER);

        assertThatThrownBy(() -> resolved.resolve(LATER.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only an open anomaly");
    }

    private NormalizationAnomalyKey key() {
        return new NormalizationAnomalyKey(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                " synthetic ",
                ProviderEntityType.COMPETITION,
                " competition-1 ",
                " 2026/2027 ",
                " REGULAR ",
                NormalizationAnomalyCode.MISSING_MAPPING);
    }
}
