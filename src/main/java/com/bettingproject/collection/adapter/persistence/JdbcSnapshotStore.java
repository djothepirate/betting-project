package com.bettingproject.collection.adapter.persistence;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.application.RawSnapshotReader;
import com.bettingproject.collection.application.SnapshotStore;
import com.bettingproject.collection.application.StoredSnapshot;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.collection.domain.SnapshotHasher;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcSnapshotStore implements SnapshotStore, RawSnapshotReader {

    private final JdbcClient jdbcClient;

    public JdbcSnapshotStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public StoredSnapshot storeAndResolve(RawSnapshot snapshot) {
        UUID candidateId = UUID.randomUUID();
        int inserted = jdbcClient.sql("""
                INSERT INTO raw_snapshot (
                    id, provider, endpoint, received_at, payload_sha256,
                    payload_compression, payload, connector_version
                ) VALUES (
                    :id, :provider, :endpoint, :receivedAt, :sha256,
                    'identity', :payload, :connectorVersion
                )
                ON CONFLICT (provider, endpoint, payload_sha256) DO NOTHING
                """)
                .param("id", candidateId)
                .param("provider", snapshot.provider())
                .param("endpoint", snapshot.endpoint())
                .param("receivedAt", snapshot.receivedAt().atOffset(ZoneOffset.UTC))
                .param("sha256", snapshot.sha256())
                .param("payload", snapshot.payload())
                .param("connectorVersion", snapshot.connectorVersion())
                .update();
        if (inserted == 1) {
            return new StoredSnapshot(candidateId, true);
        }
        UUID existingId = jdbcClient.sql("""
                SELECT id
                FROM raw_snapshot
                WHERE provider = :provider
                  AND endpoint = :endpoint
                  AND payload_sha256 = :sha256
                """)
                .param("provider", snapshot.provider())
                .param("endpoint", snapshot.endpoint())
                .param("sha256", snapshot.sha256())
                .query(UUID.class)
                .single();
        return new StoredSnapshot(existingId, false);
    }

    @Override
    public Optional<RawSnapshot> find(UUID snapshotId) {
        return jdbcClient.sql("""
                SELECT provider, endpoint, received_at, payload_sha256, payload, connector_version
                FROM raw_snapshot WHERE id = :id
                """)
                .param("id", snapshotId)
                .query((rs, row) -> {
                    var receivedAt = rs.getObject("received_at", java.time.OffsetDateTime.class).toInstant();
                    byte[] payload = rs.getBytes("payload");
                    String expectedHash = rs.getString("payload_sha256");
                    if (!SnapshotHasher.sha256(payload).equals(expectedHash)) {
                        throw new IllegalStateException("Stored raw snapshot hash mismatch");
                    }
                    return new RawSnapshot(rs.getString("provider"), rs.getString("endpoint"), receivedAt,
                            payload, expectedHash, rs.getString("connector_version"));
                }).optional();
    }
}
