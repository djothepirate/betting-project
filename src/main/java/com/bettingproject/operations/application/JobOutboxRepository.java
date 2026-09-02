package com.bettingproject.operations.application;

public interface JobOutboxRepository {

    boolean insertJobIfAbsent(PendingJob job);

    void insertOutboxMessage(PendingOutboxMessage message);
}
