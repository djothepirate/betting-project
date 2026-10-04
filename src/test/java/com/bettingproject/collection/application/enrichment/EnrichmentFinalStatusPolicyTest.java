package com.bettingproject.collection.application.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EnrichmentFinalStatusPolicyTest {
    private final EnrichmentFinalStatusPolicy policy = new EnrichmentFinalStatusPolicy();

    @Test void highlightlyFinalVocabularyIsExactAndVersioned() {
        assertThat(policy.version()).isEqualTo("enrichment-final-status-v1");
        assertThat(policy.isFinal("highlightly", "Finished")).isTrue();
        assertThat(policy.isFinal("highlightly", "Finished after penalties")).isTrue();
        assertThat(policy.isFinal("highlightly", "Finished after extra time")).isTrue();
        assertThat(policy.isFinal("highlightly", "In progress")).isFalse();
        assertThat(policy.isFinal("highlightly", "finished")).isFalse();
    }

    @Test void footballDataFinalVocabularyIsExactAndUnknownProvidersRemainNonFinal() {
        assertThat(policy.isFinal("football-data.org", "FINISHED")).isTrue();
        assertThat(policy.isFinal("football-data.org", "AWARDED")).isTrue();
        assertThat(policy.isFinal("football-data.org", "IN_PLAY")).isFalse();
        assertThat(policy.isFinal("football-data.org", "Finished")).isFalse();
        assertThat(policy.isFinal("unknown", "FINISHED")).isFalse();
        assertThat(policy.isFinal("highlightly", null)).isFalse();
    }

    @Test void finalEvidenceRequiresExplicitTimeAndPolicyVersion() {
        assertThat(new EnrichmentFinalStatusEvidence(Instant.EPOCH, policy.version()).observedAt())
                .isEqualTo(Instant.EPOCH);
    }
}
