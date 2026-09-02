package com.bettingproject.identity.adapter.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.application.StoredProviderMapping;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"control-api", "batch-worker"})
public class JdbcProviderMappingRepository implements ProviderMappingRepository {

    private final JdbcClient jdbcClient;

    public JdbcProviderMappingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ProviderMapping> find(ProviderMappingKey key) {
        return selectByKey(key, false);
    }

    @Override
    public Optional<ProviderMapping> findForUpdate(ProviderMappingKey key) {
        return selectByKey(key, true);
    }

    private Optional<ProviderMapping> selectByKey(ProviderMappingKey key, boolean forUpdate) {
        String lockingClause = forUpdate ? " FOR UPDATE" : "";
        return jdbcClient.sql("""
                SELECT id, provider, entity_type, provider_entity_id, canonical_entity_id,
                       season, phase, confidence, mapping_status, created_at, updated_at, version
                FROM provider_mapping
                WHERE provider = :provider
                  AND entity_type = :entityType
                  AND provider_entity_id = :providerEntityId
                  AND season = :season
                  AND phase = :phase
                """ + lockingClause)
                .param("provider", key.provider())
                .param("entityType", key.entityType().name())
                .param("providerEntityId", key.providerEntityId())
                .param("season", key.season())
                .param("phase", key.phase())
                .query(this::map)
                .optional();
    }

    @Override
    public StoredProviderMapping insertIfAbsentAndResolve(ProviderMapping mapping) {
        requireInitialVersion(mapping);
        int inserted = jdbcClient.sql("""
                INSERT INTO provider_mapping (
                    id, provider, entity_type, provider_entity_id, canonical_entity_id,
                    season, phase, confidence, mapping_status, created_at, updated_at, version
                ) VALUES (
                    :id, :provider, :entityType, :providerEntityId, :canonicalEntityId,
                    :season, :phase, :confidence, :mappingStatus, :createdAt, :updatedAt, :version
                )
                ON CONFLICT (provider, entity_type, provider_entity_id, season, phase) DO NOTHING
                """)
                .param("id", mapping.id())
                .param("provider", mapping.provider())
                .param("entityType", mapping.entityType().name())
                .param("providerEntityId", mapping.providerEntityId())
                .param("canonicalEntityId", mapping.canonicalEntityId())
                .param("season", mapping.season())
                .param("phase", mapping.phase())
                .param("confidence", mapping.confidence())
                .param("mappingStatus", mapping.status().name())
                .param("createdAt", mapping.createdAt().atOffset(ZoneOffset.UTC))
                .param("updatedAt", mapping.updatedAt().atOffset(ZoneOffset.UTC))
                .param("version", mapping.version())
                .update();
        if (inserted == 1) {
            return new StoredProviderMapping(mapping, true);
        }
        ProviderMapping existing = find(
                mapping.provider(),
                mapping.entityType(),
                mapping.providerEntityId(),
                mapping.season(),
                mapping.phase())
                .orElseThrow(() -> new IllegalStateException(
                        "Provider mapping could not be resolved after insert conflict"));
        return new StoredProviderMapping(existing, false);
    }

    @Override
    public StoredProviderMapping insertForDecisionIfAbsent(ProviderMapping mapping) {
        requireInitialVersion(mapping);
        int inserted = jdbcClient.sql("""
                INSERT INTO provider_mapping (
                    id, provider, entity_type, provider_entity_id, canonical_entity_id,
                    season, phase, confidence, mapping_status, created_at, updated_at, version
                ) VALUES (
                    :id, :provider, :entityType, :providerEntityId, :canonicalEntityId,
                    :season, :phase, :confidence, :mappingStatus, :createdAt, :updatedAt, :version
                )
                ON CONFLICT (provider, entity_type, provider_entity_id, season, phase) DO NOTHING
                """)
                .param("id", mapping.id())
                .param("provider", mapping.provider())
                .param("entityType", mapping.entityType().name())
                .param("providerEntityId", mapping.providerEntityId())
                .param("canonicalEntityId", mapping.canonicalEntityId())
                .param("season", mapping.season())
                .param("phase", mapping.phase())
                .param("confidence", mapping.confidence())
                .param("mappingStatus", mapping.status().name())
                .param("createdAt", mapping.createdAt().atOffset(ZoneOffset.UTC))
                .param("updatedAt", mapping.updatedAt().atOffset(ZoneOffset.UTC))
                .param("version", mapping.version())
                .update();
        if (inserted == 1) {
            return new StoredProviderMapping(mapping, true);
        }
        ProviderMapping existing = findForUpdate(mapping.key())
                .orElseThrow(() -> new IllegalStateException(
                        "Provider mapping could not be resolved after decision insert conflict"));
        return new StoredProviderMapping(existing, false);
    }

    @Override
    public boolean updateIfVersion(ProviderMapping mapping, long expectedVersion) {
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be at least 1");
        }
        if (mapping.version() != expectedVersion + 1) {
            throw new IllegalArgumentException(
                    "mapping version must be exactly one greater than expectedVersion");
        }
        return jdbcClient.sql("""
                UPDATE provider_mapping
                SET canonical_entity_id = :canonicalEntityId,
                    confidence = :confidence,
                    mapping_status = :mappingStatus,
                    updated_at = :updatedAt,
                    version = :version
                WHERE id = :id
                  AND version = :expectedVersion
                """)
                .param("canonicalEntityId", mapping.canonicalEntityId())
                .param("confidence", mapping.confidence())
                .param("mappingStatus", mapping.status().name())
                .param("updatedAt", mapping.updatedAt().atOffset(ZoneOffset.UTC))
                .param("version", mapping.version())
                .param("id", mapping.id())
                .param("expectedVersion", expectedVersion)
                .update() == 1;
    }

    private ProviderMapping map(ResultSet resultSet, int rowNumber) throws SQLException {
        BigDecimal confidence = resultSet.getBigDecimal("confidence");
        return new ProviderMapping(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("provider"),
                ProviderEntityType.valueOf(resultSet.getString("entity_type")),
                resultSet.getString("provider_entity_id"),
                resultSet.getObject("canonical_entity_id", UUID.class),
                resultSet.getString("season"),
                resultSet.getString("phase"),
                confidence == null ? null : confidence.doubleValue(),
                MappingStatus.valueOf(resultSet.getString("mapping_status")),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant(),
                resultSet.getLong("version"));
    }

    private void requireInitialVersion(ProviderMapping mapping) {
        if (mapping.version() != 1) {
            throw new IllegalArgumentException("new provider mapping must start at version 1");
        }
    }
}
