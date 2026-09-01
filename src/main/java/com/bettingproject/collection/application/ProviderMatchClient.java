package com.bettingproject.collection.application;

import java.io.IOException;

public interface ProviderMatchClient {

    String provider();

    ProviderCallResult fetch(EvidenceCollectionRequest request) throws IOException, InterruptedException;
}
