package com.bettingproject.catalog.application;

public interface ControlCommandLock {

    void acquire(String idempotencyKey);
}
