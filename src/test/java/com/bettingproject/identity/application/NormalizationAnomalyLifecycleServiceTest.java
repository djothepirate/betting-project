package com.bettingproject.identity.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.identity.domain.NormalizationAnomaly;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyCode;
import com.bettingproject.identity.domain.NormalizationAnomalyEvent;
import com.bettingproject.identity.domain.NormalizationAnomalyEventType;
import com.bettingproject.identity.domain.NormalizationAnomalyKey;
import com.bettingproject.identity.domain.ProviderEntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationAnomalyLifecycleServiceTest {

    private static final UUID SNAPSHOT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID APPLICATION_LOG_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant FIRST = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-09-01T13:00:00Z");
    private static final Instant THIRD = Instant.parse("2026-09-01T14:00:00Z");

    private FakeRepository repository;
    private List<NormalizationAnomalyEvent> events;
    private NormalizationAnomalyLifecycleService service;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        events = new ArrayList<>();
        service = new NormalizationAnomalyLifecycleService(repository, events::add);
    }

    @Test
    void opensThenObservesTheSameLogicalAnomalyWithApplicationCorrelation() {
        UUID firstId = service.recordOccurrence(
                key(), "first", APPLICATION_LOG_ID, FIRST);
        UUID secondId = service.recordOccurrence(
                key(), "second", APPLICATION_LOG_ID, SECOND);

        assertThat(secondId).isEqualTo(firstId);
        NormalizationAnomaly current = repository.byId(firstId);
        assertThat(current.status()).isEqualTo(AnomalyStatus.OPEN);
        assertThat(current.version()).isEqualTo(2);
        assertThat(current.occurrenceCount()).isEqualTo(2);
        assertThat(current.details()).isEqualTo("second");
        assertThat(events).extracting(NormalizationAnomalyEvent::eventType)
                .containsExactly(
                        NormalizationAnomalyEventType.OPENED,
                        NormalizationAnomalyEventType.OBSERVED);
        assertThat(events).extracting(NormalizationAnomalyEvent::fixtureApplicationLogId)
                .containsOnly(APPLICATION_LOG_ID);
    }

    @Test
    void completeAssessmentResolvesOnlyOpenAnomaliesNotEncountered() {
        UUID encountered = service.recordOccurrence(key(), "keep", APPLICATION_LOG_ID, FIRST);
        NormalizationAnomalyKey absentKey = new NormalizationAnomalyKey(
                SNAPSHOT_ID, "synthetic", ProviderEntityType.TEAM, "team-2", "", "",
                NormalizationAnomalyCode.AMBIGUOUS_MAPPING);
        UUID absent = service.recordOccurrence(absentKey, "resolve", APPLICATION_LOG_ID, FIRST);

        service.completeAssessment(SNAPSHOT_ID, Set.of(encountered), SECOND);

        assertThat(repository.byId(encountered).status()).isEqualTo(AnomalyStatus.OPEN);
        assertThat(repository.byId(absent).status()).isEqualTo(AnomalyStatus.RESOLVED);
        assertThat(repository.byId(absent).occurrenceCount()).isEqualTo(1);
        assertThat(events).extracting(NormalizationAnomalyEvent::eventType)
                .containsExactly(
                        NormalizationAnomalyEventType.OPENED,
                        NormalizationAnomalyEventType.OPENED,
                        NormalizationAnomalyEventType.RESOLVED);
        assertThat(events.get(2).fixtureApplicationLogId()).isNull();
    }

    @Test
    void recurrenceAfterResolutionReopensWithoutLosingHistory() {
        UUID anomalyId = service.recordOccurrence(key(), "first", APPLICATION_LOG_ID, FIRST);
        service.completeAssessment(SNAPSHOT_ID, Set.of(), SECOND);

        UUID reopenedId = service.recordOccurrence(key(), "again", APPLICATION_LOG_ID, THIRD);

        assertThat(reopenedId).isEqualTo(anomalyId);
        NormalizationAnomaly current = repository.byId(anomalyId);
        assertThat(current.status()).isEqualTo(AnomalyStatus.OPEN);
        assertThat(current.resolvedAt()).isNull();
        assertThat(current.version()).isEqualTo(3);
        assertThat(current.occurrenceCount()).isEqualTo(2);
        assertThat(events).extracting(NormalizationAnomalyEvent::eventType)
                .containsExactly(
                        NormalizationAnomalyEventType.OPENED,
                        NormalizationAnomalyEventType.RESOLVED,
                        NormalizationAnomalyEventType.REOPENED);
    }

    private NormalizationAnomalyKey key() {
        return new NormalizationAnomalyKey(
                SNAPSHOT_ID, "synthetic", ProviderEntityType.COMPETITION, "competition-1",
                "2026/2027", "REGULAR", NormalizationAnomalyCode.MISSING_MAPPING);
    }

    private static final class FakeRepository implements NormalizationAnomalyRepository {
        private final Map<UUID, NormalizationAnomaly> anomalies = new LinkedHashMap<>();

        @Override
        public StoredNormalizationAnomaly insertOrResolveForUpdate(
                NormalizationAnomaly candidate) {
            NormalizationAnomaly existing = anomalies.values().stream()
                    .filter(anomaly -> anomaly.key().equals(candidate.key()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return new StoredNormalizationAnomaly(existing, false);
            }
            anomalies.put(candidate.id(), candidate);
            return new StoredNormalizationAnomaly(candidate, true);
        }

        @Override
        public boolean updateIfVersion(NormalizationAnomaly anomaly, long expectedVersion) {
            NormalizationAnomaly current = anomalies.get(anomaly.id());
            if (current == null || current.version() != expectedVersion) {
                return false;
            }
            anomalies.put(anomaly.id(), anomaly);
            return true;
        }

        @Override
        public List<NormalizationAnomaly> findOpenBySnapshotForUpdate(UUID rawSnapshotId) {
            return anomalies.values().stream()
                    .filter(anomaly -> anomaly.rawSnapshotId().equals(rawSnapshotId))
                    .filter(anomaly -> anomaly.status() == AnomalyStatus.OPEN)
                    .toList();
        }

        NormalizationAnomaly byId(UUID id) {
            return anomalies.get(id);
        }
    }
}
