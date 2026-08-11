package com.bettingproject.collection.application;

import java.util.Objects;
import java.util.UUID;

public record StoredSnapshot(UUID id, boolean inserted) {

    public StoredSnapshot {
        id = Objects.requireNonNull(id, "id");
    }
}
