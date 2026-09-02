package com.bettingproject.catalog.adapter.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.bettingproject.catalog.application.MappingDecisionAnomalyReference;
import com.bettingproject.catalog.application.MappingDecisionAnomalyStore;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcMappingDecisionAnomalyStore implements MappingDecisionAnomalyStore {

    private final JdbcClient jdbcClient;

    public JdbcMappingDecisionAnomalyStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<MappingDecisionAnomalyReference> findOpenAnomalies(ProviderMappingKey mappingKey) {
        return jdbcClient.sql("""
                SELECT id, raw_snapshot_id
                FROM normalization_anomaly
                WHERE status = 'OPEN'
                  AND provider = :provider
                  AND entity_type = :entityType
                  AND provider_entity_id = :providerEntityId
                  AND season IS NOT DISTINCT FROM :season
                  AND phase IS NOT DISTINCT FROM :phase
                  AND anomaly_code IN (
                      'MISSING_MAPPING',
                      'AMBIGUOUS_MAPPING',
                      'REJECTED_MAPPING',
                      'MAPPING_CONFLICT'
                  )
                ORDER BY created_at, id
                """)
                .param("provider", mappingKey.provider())
                .param("entityType", mappingKey.entityType().name())
                .param("providerEntityId", mappingKey.providerEntityId())
                .param("season", mappingKey.season())
                .param("phase", mappingKey.phase())
                .query((resultSet, rowNumber) -> new MappingDecisionAnomalyReference(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("raw_snapshot_id", UUID.class)))
                .list();
    }

    @Override
    public void correlate(UUID decisionId, List<UUID> anomalyIds, Instant createdAt) {
        for (UUID anomalyId : anomalyIds) {
            jdbcClient.sql("""
                    INSERT INTO provider_mapping_decision_anomaly (
                        provider_mapping_decision_id, normalization_anomaly_id, created_at
                    ) VALUES (
                        :decisionId, :anomalyId, :createdAt
                    )
                    ON CONFLICT (
                        provider_mapping_decision_id, normalization_anomaly_id
                    ) DO NOTHING
                    """)
                    .param("decisionId", decisionId)
                    .param("anomalyId", anomalyId)
                    .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
                    .update();
        }
    }

    @Override
    public List<UUID> findAnomalyIds(UUID decisionId) {
        return jdbcClient.sql("""
                SELECT normalization_anomaly_id
                FROM provider_mapping_decision_anomaly
                WHERE provider_mapping_decision_id = :decisionId
                ORDER BY normalization_anomaly_id
                """)
                .param("decisionId", decisionId)
                .query(UUID.class)
                .list();
    }

    @Override
    public List<MappingDecisionAnomalyReference> findAnomalies(UUID decisionId) {
        return jdbcClient.sql("""
                SELECT anomaly.id, anomaly.raw_snapshot_id
                FROM provider_mapping_decision_anomaly correlation
                JOIN normalization_anomaly anomaly
                  ON anomaly.id = correlation.normalization_anomaly_id
                WHERE correlation.provider_mapping_decision_id = :decisionId
                ORDER BY anomaly.id
                """)
                .param("decisionId", decisionId)
                .query((resultSet, rowNumber) -> new MappingDecisionAnomalyReference(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("raw_snapshot_id", UUID.class)))
                .list();
    }
}
