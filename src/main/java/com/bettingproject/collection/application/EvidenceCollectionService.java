package com.bettingproject.collection.application;

import java.io.IOException;
import java.util.Objects;

public final class EvidenceCollectionService {

    private final ProviderMatchClient client;
    private final EvidenceRepository repository;

    public EvidenceCollectionService(ProviderMatchClient client, EvidenceRepository repository) {
        this.client = Objects.requireNonNull(client, "client");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public StoredEvidence collect(EvidenceCollectionRequest request) throws IOException, InterruptedException {
        Objects.requireNonNull(request, "request");
        ProviderCallResult response = client.fetch(request);
        if (!client.provider().equals(response.provider())) {
            throw new IllegalStateException("Provider response does not match the selected client");
        }
        return repository.store(request, response);
    }
}
