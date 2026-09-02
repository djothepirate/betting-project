package com.bettingproject.catalog.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.bettingproject.identity.domain.ProviderMappingDecision;

public sealed interface MappingDecisionResult {

    record Applied(
            ProviderMappingDecision decision,
            List<UUID> correlatedAnomalyIds,
            List<UUID> replayRequestIds) implements MappingDecisionResult {

        public Applied {
            decision = Objects.requireNonNull(decision, "decision");
            correlatedAnomalyIds = List.copyOf(correlatedAnomalyIds);
            replayRequestIds = List.copyOf(replayRequestIds);
        }

        public Applied(
                ProviderMappingDecision decision,
                List<UUID> correlatedAnomalyIds) {
            this(decision, correlatedAnomalyIds, List.of());
        }
    }

    record AlreadyApplied(
            ProviderMappingDecision decision,
            List<UUID> correlatedAnomalyIds,
            List<UUID> replayRequestIds) implements MappingDecisionResult {

        public AlreadyApplied {
            decision = Objects.requireNonNull(decision, "decision");
            correlatedAnomalyIds = List.copyOf(correlatedAnomalyIds);
            replayRequestIds = List.copyOf(replayRequestIds);
        }

        public AlreadyApplied(
                ProviderMappingDecision decision,
                List<UUID> correlatedAnomalyIds) {
            this(decision, correlatedAnomalyIds, List.of());
        }
    }

    record Invalid(String code) implements MappingDecisionResult {

        public Invalid {
            code = requireCode(code);
        }
    }

    record NotFound(String resource) implements MappingDecisionResult {

        public NotFound {
            resource = requireCode(resource);
        }
    }

    record VersionConflict(long expectedVersion, Long actualVersion)
            implements MappingDecisionResult {
    }

    record IdempotencyConflict() implements MappingDecisionResult {
    }

    record OperatorUnavailable() implements MappingDecisionResult {
    }

    record BusinessRuleViolation(String code) implements MappingDecisionResult {

        public BusinessRuleViolation {
            code = requireCode(code);
        }
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("result code must not be blank");
        }
        return code;
    }
}
