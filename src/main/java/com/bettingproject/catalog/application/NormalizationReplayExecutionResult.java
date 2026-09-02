package com.bettingproject.catalog.application;

import java.util.Objects;

public sealed interface NormalizationReplayExecutionResult {

    record Invalid(String code) implements NormalizationReplayExecutionResult {

        public Invalid {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("result code must not be blank");
            }
        }
    }

    record Completed(
            NormalizationReplayRequest request,
            NormalizationReplayAttempt attempt)
            implements NormalizationReplayExecutionResult {

        public Completed {
            request = Objects.requireNonNull(request, "request");
            attempt = Objects.requireNonNull(attempt, "attempt");
        }
    }

    record Failed(
            NormalizationReplayRequest request,
            NormalizationReplayAttempt attempt)
            implements NormalizationReplayExecutionResult {

        public Failed {
            request = Objects.requireNonNull(request, "request");
            attempt = Objects.requireNonNull(attempt, "attempt");
        }
    }

    record AlreadyTerminal(NormalizationReplayRequest request)
            implements NormalizationReplayExecutionResult {

        public AlreadyTerminal {
            request = Objects.requireNonNull(request, "request");
        }
    }

    record NotFound() implements NormalizationReplayExecutionResult {
    }

    record NotClaimed(NormalizationReplayRequest request)
            implements NormalizationReplayExecutionResult {

        public NotClaimed {
            request = Objects.requireNonNull(request, "request");
        }
    }
}
