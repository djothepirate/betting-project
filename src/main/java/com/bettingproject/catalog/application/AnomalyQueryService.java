package com.bettingproject.catalog.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyEventView;
import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyQueryFilter;
import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyView;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("control-api")
@Transactional(readOnly = true)
public class AnomalyQueryService {

    private final AnomalyQueryPort queryPort;

    public AnomalyQueryService(AnomalyQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    public CatalogQueryPage<AnomalyView> list(
            AnomalyQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int limit) {
        Objects.requireNonNull(filter, "filter");
        validateLimit(limit);
        return CatalogQueryPage.fromFetched(
                queryPort.fetchAnomalies(filter, anchor, limit + 1), limit);
    }

    public Optional<AnomalyView> findById(UUID anomalyId) {
        return queryPort.findAnomaly(Objects.requireNonNull(anomalyId, "anomalyId"));
    }

    public CatalogQueryPage<AnomalyEventView> listEvents(
            UUID anomalyId,
            TimestampKeysetAnchor anchor,
            int limit) {
        Objects.requireNonNull(anomalyId, "anomalyId");
        validateLimit(limit);
        return CatalogQueryPage.fromFetched(
                queryPort.fetchEvents(anomalyId, anchor, limit + 1), limit);
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }
}
