package com.bettingproject.catalog.application;

import java.util.Objects;

import com.bettingproject.catalog.domain.CanonicalFixture;

public record StoredCanonicalFixture(
        CanonicalFixture fixture,
        boolean inserted) {

    public StoredCanonicalFixture {
        fixture = Objects.requireNonNull(fixture, "fixture");
    }
}
