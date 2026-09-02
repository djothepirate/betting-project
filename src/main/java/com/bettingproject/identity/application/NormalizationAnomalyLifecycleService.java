package com.bettingproject.identity.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.identity.domain.NormalizationAnomaly;
import com.bettingproject.identity.domain.NormalizationAnomaly.AnomalyStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyEvent;
import com.bettingproject.identity.domain.NormalizationAnomalyEventType;
import com.bettingproject.identity.domain.NormalizationAnomalyKey;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"control-api", "batch-worker"})
public class NormalizationAnomalyLifecycleService {

    private static final String COMPLETE_ASSESSMENT_DETAILS =
            "Anomaly was not reproduced by a complete snapshot assessment";

    private final NormalizationAnomalyRepository anomalyRepository;
    private final NormalizationAnomalyEventJournal eventJournal;

    public NormalizationAnomalyLifecycleService(
            NormalizationAnomalyRepository anomalyRepository,
            NormalizationAnomalyEventJournal eventJournal) {
        this.anomalyRepository = anomalyRepository;
        this.eventJournal = eventJournal;
    }

    @Transactional
    public UUID recordOccurrence(
            NormalizationAnomalyKey key,
            String details,
            UUID fixtureApplicationLogId,
            Instant occurredAt) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(occurredAt, "occurredAt");
        NormalizationAnomaly candidate = NormalizationAnomaly.open(key, details, occurredAt);
        StoredNormalizationAnomaly stored = anomalyRepository.insertOrResolveForUpdate(candidate);
        if (stored.inserted()) {
            appendEvent(
                    candidate,
                    NormalizationAnomalyEventType.OPENED,
                    null,
                    fixtureApplicationLogId,
                    candidate.details(),
                    occurredAt);
            return candidate.id();
        }

        NormalizationAnomaly current = stored.anomaly();
        NormalizationAnomaly observed = current.observe(details, occurredAt);
        updateCurrent(observed, current.version());
        NormalizationAnomalyEventType eventType = current.status() == AnomalyStatus.RESOLVED
                ? NormalizationAnomalyEventType.REOPENED
                : NormalizationAnomalyEventType.OBSERVED;
        appendEvent(
                observed,
                eventType,
                current.status(),
                fixtureApplicationLogId,
                observed.details(),
                occurredAt);
        return observed.id();
    }

    @Transactional
    public void completeAssessment(
            UUID rawSnapshotId,
            Set<UUID> encounteredAnomalyIds,
            Instant completedAt) {
        Objects.requireNonNull(rawSnapshotId, "rawSnapshotId");
        Set<UUID> encountered = Set.copyOf(
                Objects.requireNonNull(encounteredAnomalyIds, "encounteredAnomalyIds"));
        Objects.requireNonNull(completedAt, "completedAt");
        for (NormalizationAnomaly current
                : anomalyRepository.findOpenBySnapshotForUpdate(rawSnapshotId)) {
            if (encountered.contains(current.id())) {
                continue;
            }
            NormalizationAnomaly resolved = current.resolve(completedAt);
            updateCurrent(resolved, current.version());
            appendEvent(
                    resolved,
                    NormalizationAnomalyEventType.RESOLVED,
                    current.status(),
                    null,
                    COMPLETE_ASSESSMENT_DETAILS,
                    completedAt);
        }
    }

    private void updateCurrent(NormalizationAnomaly anomaly, long expectedVersion) {
        if (!anomalyRepository.updateIfVersion(anomaly, expectedVersion)) {
            throw new IllegalStateException(
                    "Normalization anomaly changed concurrently: " + anomaly.id());
        }
    }

    private void appendEvent(
            NormalizationAnomaly anomaly,
            NormalizationAnomalyEventType eventType,
            AnomalyStatus previousStatus,
            UUID fixtureApplicationLogId,
            String details,
            Instant createdAt) {
        eventJournal.append(new NormalizationAnomalyEvent(
                UUID.randomUUID(),
                anomaly.id(),
                eventType,
                previousStatus,
                anomaly.status(),
                fixtureApplicationLogId,
                details,
                createdAt));
    }
}
