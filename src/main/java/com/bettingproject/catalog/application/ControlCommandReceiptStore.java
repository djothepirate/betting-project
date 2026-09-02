package com.bettingproject.catalog.application;

import java.util.Optional;

public interface ControlCommandReceiptStore {

    Optional<ControlCommandReceipt> findByIdempotencyKey(String idempotencyKey);

    void insert(ControlCommandReceipt receipt);
}
