package com.bettingproject.identity.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderMappingDecisionTest {

    private static final UUID CANONICAL_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000042");

    @Test
    void rejectsInvalidVersionSequencesAndPreviousCreationState() {
        assertThatThrownBy(() -> decision(
                MappingDecisionType.CONFIRM, 1, 3,
                MappingStatus.AMBIGUOUS, null, 0.5,
                MappingStatus.CONFIRMED, CANONICAL_ID, 1.0,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resultingVersion");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.CONFIRM, -1, 0,
                null, null, null,
                MappingStatus.CONFIRMED, CANONICAL_ID, 1.0,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resultingVersion");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.CONFIRM, 0, 1,
                MappingStatus.AMBIGUOUS, null, 0.5,
                MappingStatus.CONFIRMED, CANONICAL_ID, 1.0,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("creation decision");
    }

    @Test
    void rejectsIncompleteOrContradictoryPreviousStates() {
        assertThatThrownBy(() -> decision(
                MappingDecisionType.CONFIRM, 1, 2,
                null, null, null,
                MappingStatus.CONFIRMED, CANONICAL_ID, 1.0,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("previous status");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.REJECT, 1, 2,
                MappingStatus.CONFIRMED, null, 1.0,
                MappingStatus.REJECTED, null, null,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical entity");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.CONFIRM, 1, 2,
                MappingStatus.AMBIGUOUS, CANONICAL_ID, 0.5,
                MappingStatus.CONFIRMED, CANONICAL_ID, 1.0,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unconfirmed state");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.CONFIRM, 1, 2,
                MappingStatus.AMBIGUOUS, null, 1.1,
                MappingStatus.CONFIRMED, CANONICAL_ID, 1.0,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("previousConfidence");
    }

    @Test
    void rejectsConfirmationResultsWithoutExactConfirmedSemantics() {
        assertThatThrownBy(() -> decision(
                MappingDecisionType.CONFIRM, 0, 1,
                null, null, null,
                MappingStatus.AMBIGUOUS, null, 0.5,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confirmation requires");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.CONFIRM, 0, 1,
                null, null, null,
                MappingStatus.CONFIRMED, null, 1.0,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confirmation requires");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.CONFIRM, 0, 1,
                null, null, null,
                MappingStatus.CONFIRMED, CANONICAL_ID, null,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence 1.0");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.CONFIRM, 0, 1,
                null, null, null,
                MappingStatus.CONFIRMED, CANONICAL_ID, 0.9,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence 1.0");
    }

    @Test
    void rejectsRejectionResultsThatRetainCanonicalData() {
        assertThatThrownBy(() -> decision(
                MappingDecisionType.REJECT, 0, 1,
                null, null, null,
                MappingStatus.CONFIRMED, CANONICAL_ID, 1.0,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejection requires");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.REJECT, 0, 1,
                null, null, null,
                MappingStatus.REJECTED, CANONICAL_ID, null,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejection requires");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.REJECT, 0, 1,
                null, null, null,
                MappingStatus.REJECTED, null, 0.1,
                "operator", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejection requires");
    }

    @Test
    void rejectsInvalidOperatorAndJustificationAuditFields() {
        assertThatThrownBy(() -> decision(
                MappingDecisionType.REJECT, 0, 1,
                null, null, null,
                MappingStatus.REJECTED, null, null,
                " ", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operatorId");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.REJECT, 0, 1,
                null, null, null,
                MappingStatus.REJECTED, null, null,
                "operator\nname", "review"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.REJECT, 0, 1,
                null, null, null,
                MappingStatus.REJECTED, null, null,
                "operator", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justification");
        assertThatThrownBy(() -> decision(
                MappingDecisionType.REJECT, 0, 1,
                null, null, null,
                MappingStatus.REJECTED, null, null,
                "operator", "review\nline"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");
    }

    private ProviderMappingDecision decision(
            MappingDecisionType type,
            long expectedVersion,
            long resultingVersion,
            MappingStatus previousStatus,
            UUID previousCanonicalId,
            Double previousConfidence,
            MappingStatus resultingStatus,
            UUID resultingCanonicalId,
            Double resultingConfidence,
            String operator,
            String justification) {
        return new ProviderMappingDecision(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                type,
                expectedVersion,
                resultingVersion,
                previousStatus,
                previousCanonicalId,
                previousConfidence,
                resultingStatus,
                resultingCanonicalId,
                resultingConfidence,
                operator,
                justification,
                Instant.parse("2026-09-01T18:00:00Z"));
    }
}
