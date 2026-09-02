package com.bettingproject.identity.application;

import java.util.Objects;

import com.bettingproject.identity.domain.ProviderMapping;

public record StoredProviderMapping(
        ProviderMapping mapping,
        boolean inserted) {

    public StoredProviderMapping {
        mapping = Objects.requireNonNull(mapping, "mapping");
    }
}
