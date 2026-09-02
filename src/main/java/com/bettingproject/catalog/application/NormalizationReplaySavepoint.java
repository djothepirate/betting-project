package com.bettingproject.catalog.application;

public interface NormalizationReplaySavepoint {

    Object create();

    void rollback(Object savepoint);

    void release(Object savepoint);
}
