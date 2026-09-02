package com.bettingproject.catalog.adapter.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import com.bettingproject.catalog.application.SnapshotProvenance;

final class JdbcCatalogQueryMappings {

    private JdbcCatalogQueryMappings() {
    }

    static SnapshotProvenance snapshot(ResultSet resultSet) throws SQLException {
        return new SnapshotProvenance(
                resultSet.getObject("snapshot_id", java.util.UUID.class),
                resultSet.getString("snapshot_provider"),
                resultSet.getString("snapshot_endpoint"),
                instantOrNull(resultSet, "snapshot_requested_at"),
                instant(resultSet, "snapshot_received_at"),
                instantOrNull(resultSet, "snapshot_source_observed_at"),
                resultSet.getObject("snapshot_http_status", Integer.class),
                resultSet.getObject("snapshot_latency_ms", Long.class),
                resultSet.getObject("snapshot_quota_remaining", Long.class),
                resultSet.getString("snapshot_payload_sha256"),
                resultSet.getString("snapshot_payload_compression"),
                resultSet.getString("snapshot_connector_version"),
                instant(resultSet, "snapshot_created_at"));
    }

    static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    static java.time.Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
