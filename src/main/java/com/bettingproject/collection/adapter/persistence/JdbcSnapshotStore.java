package com.bettingproject.collection.adapter.persistence;

import java.time.ZoneOffset;
import java.util.UUID;

import com.bettingproject.collection.application.SnapshotStore;
import com.bettingproject.collection.domain.RawSnapshot;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcSnapshotStore implements SnapshotStore {

    private final JdbcClient jdbcClient;

    public JdbcSnapshotStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public boolean store(RawSnapshot snapshot) {
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
                .param("id", UUID.randomUUID())
                .param("provider", snapshot.provider())
                .param("endpoint", snapshot.endpoint())
                .param("receivedAt", snapshot.receivedAt().atOffset(ZoneOffset.UTC))
                .param("sha256", snapshot.sha256())
                .param("payload", snapshot.payload())
                .param("connectorVersion", snapshot.connectorVersion())
                .update();
        return inserted == 1;
    }
}
