package com.bettingproject.collection.application;

import java.util.Objects;

import com.bettingproject.collection.domain.SnapshotHasher;
import org.springframework.stereotype.Service;

@Service
public class ReplayService {

    public <T> ReplayResult<T> replay(byte[] payload, SnapshotParser<T> parser) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(parser, "parser");
        String sha256 = SnapshotHasher.sha256(payload);
        try {
            return new ReplayResult<>(sha256, parser.parse(payload.clone()));
        }
        catch (Exception exception) {
            throw new ReplayFailure(sha256, exception);
        }
    }
}
