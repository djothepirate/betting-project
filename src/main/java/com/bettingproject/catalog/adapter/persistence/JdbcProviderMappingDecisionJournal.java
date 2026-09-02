package com.bettingproject.catalog.adapter.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.ProviderMappingDecisionJournal;
import com.bettingproject.identity.domain.MappingDecisionType;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.ProviderMappingDecision;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("control-api")
public class JdbcProviderMappingDecisionJournal implements ProviderMappingDecisionJournal {

    private final JdbcClient jdbcClient;

    public JdbcProviderMappingDecisionJournal(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ProviderMappingDecision> find(UUID decisionId) {
        return jdbcClient.sql("""
                SELECT id, provider_mapping_id, control_command_receipt_id,
                       decision_type, expected_version, resulting_version,
                       previous_mapping_status, previous_canonical_entity_id,
                       previous_confidence, resulting_mapping_status,
                       resulting_canonical_entity_id, resulting_confidence,
                       operator_id, justification, created_at
                FROM provider_mapping_decision
                WHERE id = :decisionId
                """)
                .param("decisionId", decisionId)
                .query(this::map)
                .optional();
    }

    @Override
    public void append(ProviderMappingDecision decision) {
        jdbcClient.sql("""
                INSERT INTO provider_mapping_decision (
                    id, provider_mapping_id, control_command_receipt_id,
                    decision_type, expected_version, resulting_version,
                    previous_mapping_status, previous_canonical_entity_id,
                    previous_confidence, resulting_mapping_status,
                    resulting_canonical_entity_id, resulting_confidence,
                    operator_id, justification, created_at
                ) VALUES (
                    :id, :providerMappingId, :controlCommandReceiptId,
                    :decisionType, :expectedVersion, :resultingVersion,
                    :previousMappingStatus, :previousCanonicalEntityId,
                    :previousConfidence, :resultingMappingStatus,
                    :resultingCanonicalEntityId, :resultingConfidence,
                    :operatorId, :justification, :createdAt
                )
                """)
                .param("id", decision.id())
                .param("providerMappingId", decision.providerMappingId())
                .param("controlCommandReceiptId", decision.controlCommandReceiptId())
                .param("decisionType", decision.decisionType().name())
                .param("expectedVersion", decision.expectedVersion())
                .param("resultingVersion", decision.resultingVersion())
                .param("previousMappingStatus", enumName(decision.previousStatus()))
                .param("previousCanonicalEntityId", decision.previousCanonicalEntityId())
                .param("previousConfidence", decision.previousConfidence())
                .param("resultingMappingStatus", decision.resultingStatus().name())
                .param("resultingCanonicalEntityId", decision.resultingCanonicalEntityId())
                .param("resultingConfidence", decision.resultingConfidence())
                .param("operatorId", decision.operatorId())
                .param("justification", decision.justification())
                .param("createdAt", decision.createdAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    private ProviderMappingDecision map(ResultSet resultSet, int rowNumber) throws SQLException {
        BigDecimal previousConfidence = resultSet.getBigDecimal("previous_confidence");
        BigDecimal resultingConfidence = resultSet.getBigDecimal("resulting_confidence");
        String previousStatus = resultSet.getString("previous_mapping_status");
        return new ProviderMappingDecision(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("provider_mapping_id", UUID.class),
                resultSet.getObject("control_command_receipt_id", UUID.class),
                MappingDecisionType.valueOf(resultSet.getString("decision_type")),
                resultSet.getLong("expected_version"),
                resultSet.getLong("resulting_version"),
                previousStatus == null ? null : MappingStatus.valueOf(previousStatus),
                resultSet.getObject("previous_canonical_entity_id", UUID.class),
                previousConfidence == null ? null : previousConfidence.doubleValue(),
                MappingStatus.valueOf(resultSet.getString("resulting_mapping_status")),
                resultSet.getObject("resulting_canonical_entity_id", UUID.class),
                resultingConfidence == null ? null : resultingConfidence.doubleValue(),
                resultSet.getString("operator_id"),
                resultSet.getString("justification"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
