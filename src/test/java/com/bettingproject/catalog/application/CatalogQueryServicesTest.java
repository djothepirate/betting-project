package com.bettingproject.catalog.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyEventView;
import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyQueryFilter;
import com.bettingproject.catalog.application.AnomalyQueryPort.AnomalyView;
import com.bettingproject.catalog.application.MappingQueryPort.DecisionAnomalyView;
import com.bettingproject.catalog.application.MappingQueryPort.MappingDecisionQueryFilter;
import com.bettingproject.catalog.application.MappingQueryPort.MappingDecisionView;
import com.bettingproject.catalog.application.MappingQueryPort.MappingQueryFilter;
import com.bettingproject.catalog.application.MappingQueryPort.MappingView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayAnomalyEventView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayApplicationView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayAttemptView;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayQueryFilter;
import com.bettingproject.catalog.application.ReplayQueryPort.ReplayRequestView;
import com.bettingproject.identity.domain.MappingDecisionType;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyEventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CatalogQueryServicesTest {

    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");

    @Test
    void anomalyServiceRequestsOneExtraRowAndReturnsAStablePage() {
        FakeAnomalyPort port = new FakeAnomalyPort();
        AnomalyQueryService service = new AnomalyQueryService(port);
        UUID anomalyId = UUID.randomUUID();
        port.events.add(event(anomalyId, 1));
        port.events.add(event(anomalyId, 2));
        port.events.add(event(anomalyId, 3));

        CatalogQueryPage<AnomalyEventView> page = service.listEvents(anomalyId, null, 2);

        assertThat(port.lastFetchLimit).isEqualTo(3);
        assertThat(page.items()).hasSize(2);
        assertThat(page.hasNext()).isTrue();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.listEvents(anomalyId, null, 101));
    }

    @Test
    void mappingServicePreservesTheTypedAnchorAndTrimsTheExtraDecision() {
        FakeMappingPort port = new FakeMappingPort();
        MappingQueryService service = new MappingQueryService(port);
        TimestampKeysetAnchor anchor = new TimestampKeysetAnchor(NOW, UUID.randomUUID());
        port.decisions.add(decision(1));
        port.decisions.add(decision(2));

        CatalogQueryPage<MappingDecisionView> page = service.listDecisions(
                new MappingDecisionQueryFilter(null), anchor, 1);

        assertThat(port.lastAnchor).isEqualTo(anchor);
        assertThat(port.lastFetchLimit).isEqualTo(2);
        assertThat(page.items()).containsExactly(port.decisions.getFirst());
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void replayServiceConfinesAttemptsToTheRequestedParentAndValidatesLimit() {
        FakeReplayPort port = new FakeReplayPort();
        ReplayQueryService service = new ReplayQueryService(port);
        UUID requestId = UUID.randomUUID();
        AttemptKeysetAnchor anchor = new AttemptKeysetAnchor(3, UUID.randomUUID());
        port.attempts.add(attempt(requestId, 2));

        CatalogQueryPage<ReplayAttemptView> page = service.listAttempts(requestId, anchor, 50);

        assertThat(port.lastRequestId).isEqualTo(requestId);
        assertThat(port.lastAttemptAnchor).isEqualTo(anchor);
        assertThat(port.lastFetchLimit).isEqualTo(51);
        assertThat(page.items()).hasSize(1);
        assertThat(page.hasNext()).isFalse();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.listAttempts(requestId, null, 0));
    }

    private static AnomalyEventView event(UUID anomalyId, int sequence) {
        return new AnomalyEventView(
                UUID.randomUUID(), anomalyId, NormalizationAnomalyEventType.OPENED,
                null, AnomalyStatus.OPEN, null, "event-" + sequence, NOW.plusSeconds(sequence));
    }

    private static MappingDecisionView decision(int sequence) {
        return new MappingDecisionView(
                UUID.randomUUID(), UUID.randomUUID(),
                new com.bettingproject.identity.domain.ProviderMappingKey(
                        "provider", com.bettingproject.identity.domain.ProviderEntityType.TEAM,
                        "team-" + sequence, "", ""),
                MappingDecisionType.REJECT, 0, 1,
                null, null, null, MappingStatus.REJECTED, null, null,
                "operator", "reason", NOW.plusSeconds(sequence));
    }

    private static ReplayAttemptView attempt(UUID requestId, int attemptNumber) {
        return new ReplayAttemptView(
                UUID.randomUUID(), requestId, attemptNumber,
                NormalizationReplayAttemptOutcome.FAILED_RETRYABLE,
                "0".repeat(64), "0".repeat(64), null,
                null, null, null, null, null,
                "NORMALIZATION_FAILED", "Replay failed", NOW, NOW);
    }

    private static final class FakeAnomalyPort implements AnomalyQueryPort {
        private final List<AnomalyEventView> events = new ArrayList<>();
        private int lastFetchLimit;

        @Override
        public List<AnomalyView> fetchAnomalies(
                AnomalyQueryFilter filter, TimestampKeysetAnchor anchor, int fetchLimit) {
            return List.of();
        }

        @Override
        public Optional<AnomalyView> findAnomaly(UUID anomalyId) {
            return Optional.empty();
        }

        @Override
        public List<AnomalyEventView> fetchEvents(
                UUID anomalyId, TimestampKeysetAnchor anchor, int fetchLimit) {
            lastFetchLimit = fetchLimit;
            return List.copyOf(events);
        }
    }

    private static final class FakeMappingPort implements MappingQueryPort {
        private final List<MappingDecisionView> decisions = new ArrayList<>();
        private TimestampKeysetAnchor lastAnchor;
        private int lastFetchLimit;

        @Override
        public List<MappingView> fetchMappings(
                MappingQueryFilter filter, TimestampKeysetAnchor anchor, int fetchLimit) {
            return List.of();
        }

        @Override
        public Optional<MappingView> findMapping(UUID mappingId) {
            return Optional.empty();
        }

        @Override
        public List<MappingDecisionView> fetchDecisions(
                MappingDecisionQueryFilter filter, TimestampKeysetAnchor anchor, int fetchLimit) {
            lastAnchor = anchor;
            lastFetchLimit = fetchLimit;
            return List.copyOf(decisions);
        }

        @Override
        public Optional<MappingDecisionView> findDecision(UUID decisionId) {
            return Optional.empty();
        }

        @Override
        public List<DecisionAnomalyView> fetchCorrelatedAnomalies(
                UUID decisionId, CorrelationKeysetAnchor anchor, int fetchLimit) {
            return List.of();
        }
    }

    private static final class FakeReplayPort implements ReplayQueryPort {
        private final List<ReplayAttemptView> attempts = new ArrayList<>();
        private UUID lastRequestId;
        private AttemptKeysetAnchor lastAttemptAnchor;
        private int lastFetchLimit;

        @Override
        public List<ReplayRequestView> fetchRequests(
                ReplayQueryFilter filter, TimestampKeysetAnchor anchor, int fetchLimit) {
            return List.of();
        }

        @Override
        public Optional<ReplayRequestView> findRequest(UUID requestId) {
            return Optional.empty();
        }

        @Override
        public List<ReplayAttemptView> fetchAttempts(
                UUID requestId, AttemptKeysetAnchor anchor, int fetchLimit) {
            lastRequestId = requestId;
            lastAttemptAnchor = anchor;
            lastFetchLimit = fetchLimit;
            return List.copyOf(attempts);
        }

        @Override
        public List<ReplayApplicationView> fetchApplications(
                UUID requestId, UUID attemptId, CorrelationKeysetAnchor anchor, int fetchLimit) {
            return List.of();
        }

        @Override
        public List<ReplayAnomalyEventView> fetchAnomalyEvents(
                UUID requestId, UUID attemptId, CorrelationKeysetAnchor anchor, int fetchLimit) {
            return List.of();
        }
    }
}
