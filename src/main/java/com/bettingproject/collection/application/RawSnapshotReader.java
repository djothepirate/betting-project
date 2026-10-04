package com.bettingproject.collection.application;

import java.util.Optional;
import java.util.UUID;
import com.bettingproject.collection.domain.RawSnapshot;

/** Read-only stored-snapshot port. File replay remains independent of PostgreSQL. */
public interface RawSnapshotReader {
    Optional<RawSnapshot> find(UUID snapshotId);
}
