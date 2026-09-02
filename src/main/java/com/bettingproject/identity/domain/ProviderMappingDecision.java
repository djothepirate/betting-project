package com.bettingproject.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProviderMappingDecision(
        UUID id,
        UUID providerMappingId,
        UUID controlCommandReceiptId,
        MappingDecisionType decisionType,
        long expectedVersion,
        long resultingVersion,
        MappingStatus previousStatus,
        UUID previousCanonicalEntityId,
        Double previousConfidence,
        MappingStatus resultingStatus,
        UUID resultingCanonicalEntityId,
        Double resultingConfidence,
        String operatorId,
        String justification,
        Instant createdAt) {

    public ProviderMappingDecision {
        id = Objects.requireNonNull(id, "id");
        providerMappingId = Objects.requireNonNull(providerMappingId, "providerMappingId");
        controlCommandReceiptId = Objects.requireNonNull(
                controlCommandReceiptId, "controlCommandReceiptId");
        decisionType = Objects.requireNonNull(decisionType, "decisionType");
        if (expectedVersion < 0 || resultingVersion != expectedVersion + 1) {
            throw new IllegalArgumentException("resultingVersion must equal expectedVersion + 1");
        }
        validatePreviousState(
                expectedVersion, previousStatus, previousCanonicalEntityId, previousConfidence);
        resultingStatus = Objects.requireNonNull(resultingStatus, "resultingStatus");
        validateResultingState(
                decisionType, resultingStatus, resultingCanonicalEntityId, resultingConfidence);
        operatorId = requireText(operatorId, "operatorId", 100);
        if (containsControlCharacter(operatorId)) {
            throw new IllegalArgumentException("operatorId must not contain control characters");
        }
        justification = requireText(justification, "justification", 1_000);
        if (containsControlCharacter(justification)) {
            throw new IllegalArgumentException("justification must not contain control characters");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static void validatePreviousState(
            long expectedVersion,
            MappingStatus previousStatus,
            UUID previousCanonicalEntityId,
            Double previousConfidence) {
        validateConfidence(previousConfidence, "previousConfidence");
        if (expectedVersion == 0) {
            if (previousStatus != null
                    || previousCanonicalEntityId != null
                    || previousConfidence != null) {
                throw new IllegalArgumentException(
                        "creation decision must not carry a previous mapping state");
            }
            return;
        }
        if (previousStatus == null) {
            throw new IllegalArgumentException(
                    "existing mapping decision requires a previous status");
        }
        if (previousStatus == MappingStatus.CONFIRMED && previousCanonicalEntityId == null) {
            throw new IllegalArgumentException(
                    "previous confirmed state requires a canonical entity");
        }
        if (previousStatus != MappingStatus.CONFIRMED && previousCanonicalEntityId != null) {
            throw new IllegalArgumentException(
                    "previous unconfirmed state must not carry a canonical entity");
        }
    }

    private static void validateResultingState(
            MappingDecisionType decisionType,
            MappingStatus status,
            UUID canonicalEntityId,
            Double confidence) {
        validateConfidence(confidence, "resultingConfidence");
        if (decisionType == MappingDecisionType.CONFIRM) {
            if (status != MappingStatus.CONFIRMED
                    || canonicalEntityId == null
                    || confidence == null
                    || Double.compare(confidence, 1.0) != 0) {
                throw new IllegalArgumentException(
                        "confirmation requires a confirmed mapping, canonical entity "
                                + "and confidence 1.0");
            }
            return;
        }
        if (status != MappingStatus.REJECTED
                || canonicalEntityId != null
                || confidence != null) {
            throw new IllegalArgumentException(
                    "rejection requires a rejected mapping without canonical entity "
                            + "or confidence");
        }
    }

    private static void validateConfidence(Double confidence, String name) {
        if (confidence != null && (confidence < 0 || confidence > 1)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    name + " must contain at most " + maximumLength + " characters");
        }
        return normalized;
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
