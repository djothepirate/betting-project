package com.bettingproject.catalog.application;

import java.util.Objects;

public sealed interface OperatorIdentityResult {

    record Available(OperatorIdentity identity) implements OperatorIdentityResult {

        public Available {
            identity = Objects.requireNonNull(identity, "identity");
        }
    }

    record Unavailable() implements OperatorIdentityResult {
    }
}
