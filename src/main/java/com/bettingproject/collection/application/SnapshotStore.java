package com.bettingproject.collection.application;

import com.bettingproject.collection.domain.RawSnapshot;

public interface SnapshotStore {

    boolean store(RawSnapshot snapshot);
}
