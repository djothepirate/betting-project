package com.bettingproject.catalog.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredRawSnapshotReader {

    Optional<StoredRawSnapshot> findById(UUID snapshotId);

    List<StoredRawSnapshot> findByPayloadSha256(String payloadSha256);
}
