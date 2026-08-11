package com.bettingproject.identity.adapter.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
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
    public Optional<ProviderMapping> find(
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase) {
        return jdbcClient.sql("""
                SELECT id, provider, entity_type, provider_entity_id, canonical_entity_id,
                       season, phase, confidence, mapping_status, created_at, updated_at
                FROM provider_mapping
                WHERE provider = :provider
                  AND entity_type = :entityType
                  AND provider_entity_id = :providerEntityId
                  AND season = :season
                  AND phase = :phase
                """)
                .param("provider", provider)
                .param("entityType", entityType.name())
                .param("providerEntityId", providerEntityId)
                .param("season", normalize(season))
                .param("phase", normalize(phase))
                .query(this::map)
                .optional();
    }

    @Override
    public void save(ProviderMapping mapping) {
        jdbcClient.sql("""
                INSERT INTO provider_mapping (
                    id, provider, entity_type, provider_entity_id, canonical_entity_id,
                    season, phase, confidence, mapping_status, created_at, updated_at
                ) VALUES (
                    :id, :provider, :entityType, :providerEntityId, :canonicalEntityId,
                    :season, :phase, :confidence, :mappingStatus, :createdAt, :updatedAt
                )
                ON CONFLICT (provider, entity_type, provider_entity_id, season, phase)
                DO UPDATE SET
                    canonical_entity_id = EXCLUDED.canonical_entity_id,
                    confidence = EXCLUDED.confidence,
                    mapping_status = EXCLUDED.mapping_status,
                    updated_at = EXCLUDED.updated_at
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
                .update();
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
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
