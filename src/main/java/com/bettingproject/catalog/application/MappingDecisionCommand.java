package com.bettingproject.catalog.application;

import java.util.UUID;

import com.bettingproject.identity.domain.MappingDecisionType;
import com.bettingproject.identity.domain.ProviderMappingKey;

public record MappingDecisionCommand(
        ProviderMappingKey mappingKey,
        MappingDecisionType decisionType,
        UUID canonicalEntityId,
        Long expectedVersion,
        String idempotencyKey,
        String justification) {

    public static MappingDecisionCommand confirm(
            ProviderMappingKey mappingKey,
            UUID canonicalEntityId,
            Long expectedVersion,
            String idempotencyKey,
            String justification) {
        return new MappingDecisionCommand(
                mappingKey,
                MappingDecisionType.CONFIRM,
                canonicalEntityId,
                expectedVersion,
                idempotencyKey,
                justification);
    }

    public static MappingDecisionCommand reject(
            ProviderMappingKey mappingKey,
            Long expectedVersion,
            String idempotencyKey,
            String justification) {
        return new MappingDecisionCommand(
                mappingKey,
                MappingDecisionType.REJECT,
                null,
                expectedVersion,
                idempotencyKey,
                justification);
    }

    @Override
    public String toString() {
        return "MappingDecisionCommand["
                + "mappingKey=" + mappingKey
                + ", decisionType=" + decisionType
                + ", canonicalEntityId=" + canonicalEntityId
                + ", expectedVersion=" + expectedVersion
                + ", idempotencyKey=" + idempotencyKey
                + ", justification=[REDACTED]]";
    }
}
