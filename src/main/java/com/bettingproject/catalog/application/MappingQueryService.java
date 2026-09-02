package com.bettingproject.catalog.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.MappingQueryPort.DecisionAnomalyView;
import com.bettingproject.catalog.application.MappingQueryPort.MappingDecisionQueryFilter;
import com.bettingproject.catalog.application.MappingQueryPort.MappingDecisionView;
import com.bettingproject.catalog.application.MappingQueryPort.MappingQueryFilter;
import com.bettingproject.catalog.application.MappingQueryPort.MappingView;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("control-api")
@Transactional(readOnly = true)
public class MappingQueryService {

    private final MappingQueryPort queryPort;

    public MappingQueryService(MappingQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    public CatalogQueryPage<MappingView> listMappings(
            MappingQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int limit) {
        Objects.requireNonNull(filter, "filter");
        validateLimit(limit);
        return CatalogQueryPage.fromFetched(
                queryPort.fetchMappings(filter, anchor, limit + 1), limit);
    }

    public Optional<MappingView> findMapping(UUID mappingId) {
        return queryPort.findMapping(Objects.requireNonNull(mappingId, "mappingId"));
    }

    public CatalogQueryPage<MappingDecisionView> listDecisions(
            MappingDecisionQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int limit) {
        Objects.requireNonNull(filter, "filter");
        validateLimit(limit);
        return CatalogQueryPage.fromFetched(
                queryPort.fetchDecisions(filter, anchor, limit + 1), limit);
    }

    public Optional<MappingDecisionView> findDecision(UUID decisionId) {
        return queryPort.findDecision(Objects.requireNonNull(decisionId, "decisionId"));
    }

    public CatalogQueryPage<DecisionAnomalyView> listCorrelatedAnomalies(
            UUID decisionId,
            CorrelationKeysetAnchor anchor,
            int limit) {
        Objects.requireNonNull(decisionId, "decisionId");
        validateLimit(limit);
        return CatalogQueryPage.fromFetched(
                queryPort.fetchCorrelatedAnomalies(decisionId, anchor, limit + 1), limit);
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }
}
