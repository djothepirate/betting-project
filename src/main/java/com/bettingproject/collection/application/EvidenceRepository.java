package com.bettingproject.collection.application;

import java.io.IOException;

public interface EvidenceRepository {

    StoredEvidence store(EvidenceCollectionRequest request, ProviderCallResult response) throws IOException;
}
