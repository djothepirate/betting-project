package com.bettingproject.catalog.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.ReplayQueryPort.ReplayAnomalyEventView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayApplicationView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayAttemptView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayQueryFilter;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayRequestView;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("control-api")
@Transactional(readOnly = true)
public class ReplayQueryService {

    private final ReplayQueryPort queryPort;

    public ReplayQueryService(ReplayQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    public CatalogQueryPage<ReplayRequestView> listRequests(
            ReplayQueryFilter filter,
            TimestampKeysetAnchor anchor,
            int limit) {
        Objects.requireNonNull(filter, "filter");
        validateLimit(limit);
        return CatalogQueryPage.fromFetched(
                queryPort.fetchRequests(filter, anchor, limit + 1), limit);
    }

    public Optional<ReplayRequestView> findRequest(UUID requestId) {
        return queryPort.findRequest(Objects.requireNonNull(requestId, "requestId"));
    }

    public CatalogQueryPage<ReplayAttemptView> listAttempts(
            UUID requestId,
            AttemptKeysetAnchor anchor,
            int limit) {
        Objects.requireNonNull(requestId, "requestId");
        validateLimit(limit);
        return CatalogQueryPage.fromFetched(
                queryPort.fetchAttempts(requestId, anchor, limit + 1), limit);
    }

    public CatalogQueryPage<ReplayApplicationView> listApplications(
            UUID requestId,
            UUID attemptId,
            CorrelationKeysetAnchor anchor,
            int limit) {
        requireParentIds(requestId, attemptId);
        validateLimit(limit);
        return CatalogQueryPage.fromFetched(
                queryPort.fetchApplications(requestId, attemptId, anchor, limit + 1), limit);
    }

    public CatalogQueryPage<ReplayAnomalyEventView> listAnomalyEvents(
            UUID requestId,
            UUID attemptId,
            CorrelationKeysetAnchor anchor,
            int limit) {
        requireParentIds(requestId, attemptId);
        validateLimit(limit);
        return CatalogQueryPage.fromFetched(
                queryPort.fetchAnomalyEvents(requestId, attemptId, anchor, limit + 1), limit);
    }

    private static void requireParentIds(UUID requestId, UUID attemptId) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(attemptId, "attemptId");
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }
}
