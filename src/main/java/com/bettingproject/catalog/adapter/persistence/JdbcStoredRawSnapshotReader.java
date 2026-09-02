package com.bettingproject.catalog.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.StoredRawSnapshot;
import com.bettingproject.catalog.application.StoredRawSnapshotReader;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcStoredRawSnapshotReader implements StoredRawSnapshotReader {

    private final JdbcClient jdbcClient;

    public JdbcStoredRawSnapshotReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<StoredRawSnapshot> findById(UUID snapshotId) {
        return jdbcClient.sql(BASE_SELECT + " WHERE id = :snapshotId")
                .param("snapshotId", snapshotId)
                .query(this::map)
                .optional();
    }

    @Override
    public List<StoredRawSnapshot> findByPayloadSha256(String payloadSha256) {
        return jdbcClient.sql(BASE_SELECT + """
                 WHERE payload_sha256 = :payloadSha256
                 ORDER BY id
                 LIMIT 2
                """)
                .param("payloadSha256", payloadSha256)
                .query(this::map)
                .list();
    }

    private StoredRawSnapshot map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StoredRawSnapshot(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("provider"),
                resultSet.getString("endpoint"),
                resultSet.getObject("received_at", OffsetDateTime.class).toInstant(),
                resultSet.getString("payload_sha256"),
                resultSet.getString("payload_compression"),
                resultSet.getBytes("payload"),
                resultSet.getString("connector_version"));
    }

    private static final String BASE_SELECT = """
            SELECT id, provider, endpoint, received_at, payload_sha256,
                   payload_compression, payload, connector_version
            FROM raw_snapshot
            """;
}
