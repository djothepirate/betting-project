package com.bettingproject.collection.application;

@FunctionalInterface
public interface SnapshotParser<T> {

    T parse(byte[] payload) throws Exception;
}
