package com.bettingproject.collection.application;

import com.bettingproject.collection.domain.RawSnapshot;

public interface SnapshotStore {

    StoredSnapshot storeAndResolve(RawSnapshot snapshot);

    default boolean store(RawSnapshot snapshot) {
        return storeAndResolve(snapshot).inserted();
    }
}
