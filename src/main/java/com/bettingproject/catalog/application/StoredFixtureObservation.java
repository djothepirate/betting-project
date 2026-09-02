package com.bettingproject.catalog.application;

import java.util.Objects;
import java.util.UUID;

public record StoredFixtureObservation(UUID id, boolean inserted) {

    public StoredFixtureObservation {
        id = Objects.requireNonNull(id, "id");
    }
}
