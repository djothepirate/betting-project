package com.bettingproject.catalog.application;

import java.util.Objects;

public sealed interface NormalizationReplayRequestResult {

    record Created(NormalizationReplayRequest request)
            implements NormalizationReplayRequestResult {

        public Created {
            request = Objects.requireNonNull(request, "request");
        }
    }

    record AlreadyCreated(NormalizationReplayRequest request)
            implements NormalizationReplayRequestResult {

        public AlreadyCreated {
            request = Objects.requireNonNull(request, "request");
        }
    }

    record Invalid(String code) implements NormalizationReplayRequestResult {

        public Invalid {
            code = requireCode(code);
        }
    }

    record NotFound(String resource) implements NormalizationReplayRequestResult {

        public NotFound {
            resource = requireCode(resource);
        }
    }

    record AmbiguousPayloadSha256(String payloadSha256)
            implements NormalizationReplayRequestResult {

        public AmbiguousPayloadSha256 {
            payloadSha256 = Objects.requireNonNull(payloadSha256, "payloadSha256");
        }
    }

    record IdempotencyConflict() implements NormalizationReplayRequestResult {
    }

    private static String requireCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("result code must not be blank");
        }
        return value;
    }
}
