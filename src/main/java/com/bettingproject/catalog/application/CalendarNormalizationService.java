package com.bettingproject.catalog.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.catalog.domain.CanonicalFixture;
import com.bettingproject.catalog.domain.CanonicalSeason;
import com.bettingproject.catalog.domain.FixtureObservation;
import com.bettingproject.catalog.domain.FixtureObservation.ObservationStatus;
import com.bettingproject.catalog.domain.FixtureStatus;
import com.bettingproject.collection.application.CalendarSnapshot;
import com.bettingproject.collection.application.CalendarSnapshotSchemas;
import com.bettingproject.collection.application.DiscoveredCompetition;
import com.bettingproject.collection.application.DiscoveredFixture;
import com.bettingproject.collection.application.DiscoveredTeam;
import com.bettingproject.collection.application.SnapshotParser;
import com.bettingproject.collection.application.SnapshotStore;
import com.bettingproject.collection.application.StoredSnapshot;
import com.bettingproject.collection.application.UnsupportedCalendarSchemaException;
import com.bettingproject.collection.domain.RawSnapshot;
import com.bettingproject.identity.application.NormalizationAnomalyRepository;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.NormalizationAnomaly;
import com.bettingproject.identity.domain.NormalizationAnomalyCode;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"control-api", "batch-worker"})
public class CalendarNormalizationService {

    private final SnapshotStore snapshotStore;
    private final SnapshotParser<CalendarSnapshot> snapshotParser;
    private final CatalogRepository catalogRepository;
    private final ProviderMappingRepository mappingRepository;
    private final NormalizationAnomalyRepository anomalyRepository;
    private final Clock clock;

    public CalendarNormalizationService(
            SnapshotStore snapshotStore,
            SnapshotParser<CalendarSnapshot> snapshotParser,
            CatalogRepository catalogRepository,
            ProviderMappingRepository mappingRepository,
            NormalizationAnomalyRepository anomalyRepository,
            Clock clock) {
        this.snapshotStore = snapshotStore;
        this.snapshotParser = snapshotParser;
        this.catalogRepository = catalogRepository;
        this.mappingRepository = mappingRepository;
        this.anomalyRepository = anomalyRepository;
        this.clock = clock;
    }

    @Transactional
    public NormalizationResult normalize(RawSnapshot rawSnapshot) {
        Objects.requireNonNull(rawSnapshot, "rawSnapshot");
        StoredSnapshot storedSnapshot = snapshotStore.storeAndResolve(rawSnapshot);
        CalendarSnapshot calendarSnapshot;
        try {
            calendarSnapshot = snapshotParser.parse(rawSnapshot.payload());
        }
        catch (UnsupportedCalendarSchemaException exception) {
            recordSnapshotAnomaly(
                    storedSnapshot.id(), rawSnapshot, NormalizationAnomalyCode.UNSUPPORTED_SCHEMA,
                    exception.getMessage());
            return failedResult(storedSnapshot, rawSnapshot, 1);
        }
        catch (Exception exception) {
            recordSnapshotAnomaly(
                    storedSnapshot.id(), rawSnapshot, NormalizationAnomalyCode.INVALID_SNAPSHOT,
                    safeMessage(exception));
            return failedResult(storedSnapshot, rawSnapshot, 1);
        }

        if (!rawSnapshot.provider().equals(calendarSnapshot.provider())) {
            recordSnapshotAnomaly(
                    storedSnapshot.id(), rawSnapshot, NormalizationAnomalyCode.INVALID_SNAPSHOT,
                    "Snapshot provider does not match payload provider");
            return failedResult(storedSnapshot, rawSnapshot, 1);
        }
        if (!CalendarSnapshotSchemas.CANONICAL_V2.equals(calendarSnapshot.schemaVersion())) {
            recordSnapshotAnomaly(
                    storedSnapshot.id(), rawSnapshot,
                    NormalizationAnomalyCode.LEGACY_SCHEMA_NOT_NORMALIZABLE,
                    "Schema " + calendarSnapshot.schemaVersion() + " remains replayable but lacks canonical fields");
            return failedResult(storedSnapshot, rawSnapshot, 1);
        }

        Counters counters = new Counters();
        Instant observedAt = calendarSnapshot.observedAt() == null
                ? rawSnapshot.receivedAt()
                : calendarSnapshot.observedAt();
        for (DiscoveredFixture fixture : calendarSnapshot.fixtures()) {
            normalizeFixture(storedSnapshot.id(), calendarSnapshot.provider(), observedAt, fixture, counters);
        }
        return new NormalizationResult(
                storedSnapshot.id(), rawSnapshot.sha256(), storedSnapshot.inserted(), true,
                counters.created, counters.updated, counters.unchanged, counters.blocked, counters.anomalies);
    }

    private void normalizeFixture(
            UUID snapshotId,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            Counters counters) {
        Instant now = clock.instant();
        DiscoveredCompetition competition = source.competition();
        if (!hasCanonicalFields(competition, source.homeTeam(), source.awayTeam())) {
            recordAnomaly(
                    snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                    NormalizationAnomalyCode.INVALID_FIXTURE,
                    "Schema v2 fixture requires competition, season, phase and provider entity identifiers",
                    now, counters);
            insertObservation(snapshotId, null, provider, observedAt, source,
                    ObservationStatus.REJECTED, NormalizationAnomalyCode.INVALID_FIXTURE, now);
            counters.blocked++;
            return;
        }

        FixtureStatus fixtureStatus;
        try {
            fixtureStatus = FixtureStatus.valueOf(source.status());
        }
        catch (IllegalArgumentException exception) {
            recordAnomaly(
                    snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                    NormalizationAnomalyCode.INVALID_FIXTURE,
                    "Unsupported canonical fixture status: " + source.status(), now, counters);
            insertObservation(snapshotId, null, provider, observedAt, source,
                    ObservationStatus.REJECTED, NormalizationAnomalyCode.INVALID_FIXTURE, now);
            counters.blocked++;
            return;
        }

        MappingResolution competitionMapping = resolveMapping(
                snapshotId, provider, ProviderEntityType.COMPETITION,
                competition.providerCompetitionId(), competition.season(), competition.phase(),
                competition.name(), now, counters);
        MappingResolution homeMapping = resolveMapping(
                snapshotId, provider, ProviderEntityType.TEAM,
                source.homeTeam().providerTeamId(), "", "", source.homeTeam().name(), now, counters);
        MappingResolution awayMapping = resolveMapping(
                snapshotId, provider, ProviderEntityType.TEAM,
                source.awayTeam().providerTeamId(), "", "", source.awayTeam().name(), now, counters);

        NormalizationAnomalyCode blockingCode = firstFailure(competitionMapping, homeMapping, awayMapping);
        if (blockingCode != null) {
            insertObservation(snapshotId, null, provider, observedAt, source,
                    ObservationStatus.BLOCKED, blockingCode, now);
            counters.blocked++;
            return;
        }
        if (homeMapping.canonicalId().equals(awayMapping.canonicalId())) {
            recordAnomaly(
                    snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                    NormalizationAnomalyCode.INVALID_FIXTURE,
                    "Home and away provider mappings resolve to the same canonical team", now, counters);
            insertObservation(snapshotId, null, provider, observedAt, source,
                    ObservationStatus.REJECTED, NormalizationAnomalyCode.INVALID_FIXTURE, now);
            counters.blocked++;
            return;
        }

        CanonicalSeason season = catalogRepository
                .findSeason(competitionMapping.canonicalId(), competition.season())
                .orElseGet(() -> {
                    CanonicalSeason createdSeason = new CanonicalSeason(
                            UUID.randomUUID(), competitionMapping.canonicalId(), competition.season(),
                            null, null, now, now);
                    catalogRepository.insertSeason(createdSeason);
                    return createdSeason;
                });

        FixtureResolution fixtureResolution = resolveFixture(
                snapshotId,
                provider,
                source,
                fixtureStatus,
                competitionMapping.canonicalId(),
                season.id(),
                homeMapping.canonicalId(),
                awayMapping.canonicalId(),
                now,
                counters);
        if (fixtureResolution.blockingCode() != null) {
            insertObservation(snapshotId, null, provider, observedAt, source,
                    ObservationStatus.BLOCKED, fixtureResolution.blockingCode(), now);
            counters.blocked++;
            return;
        }

        switch (fixtureResolution.change()) {
            case CREATED -> counters.created++;
            case UPDATED -> counters.updated++;
            case UNCHANGED -> counters.unchanged++;
        }
        insertObservation(snapshotId, fixtureResolution.fixtureId(), provider, observedAt, source,
                ObservationStatus.NORMALIZED, null, now);
    }

    private FixtureResolution resolveFixture(
            UUID snapshotId,
            String provider,
            DiscoveredFixture source,
            FixtureStatus status,
            UUID competitionId,
            UUID seasonId,
            UUID homeTeamId,
            UUID awayTeamId,
            Instant now,
            Counters counters) {
        DiscoveredCompetition competition = source.competition();
        Optional<ProviderMapping> existingMapping = mappingRepository.find(
                provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                competition.season(), competition.phase());
        if (existingMapping.isPresent()) {
            ProviderMapping mapping = existingMapping.get();
            if (mapping.status() != MappingStatus.CONFIRMED) {
                NormalizationAnomalyCode code = mapping.status() == MappingStatus.AMBIGUOUS
                        ? NormalizationAnomalyCode.AMBIGUOUS_MAPPING
                        : NormalizationAnomalyCode.REJECTED_MAPPING;
                recordAnomaly(
                        snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                        code, "Fixture mapping is " + mapping.status(), now, counters);
                return FixtureResolution.blocked(code);
            }
            Optional<CanonicalFixture> mappedFixture = catalogRepository.findFixture(mapping.canonicalEntityId());
            if (mappedFixture.isEmpty()) {
                recordAnomaly(
                        snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                        NormalizationAnomalyCode.MAPPING_CONFLICT,
                        "Confirmed fixture mapping points to a missing canonical fixture", now, counters);
                return FixtureResolution.blocked(NormalizationAnomalyCode.MAPPING_CONFLICT);
            }
            CanonicalFixture fixture = mappedFixture.get();
            if (!fixture.competitionId().equals(competitionId)
                    || !fixture.seasonId().equals(seasonId)
                    || !fixture.homeTeamId().equals(homeTeamId)
                    || !fixture.awayTeamId().equals(awayTeamId)) {
                recordAnomaly(
                        snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                        NormalizationAnomalyCode.MAPPING_CONFLICT,
                        "Source participants or competition conflict with the confirmed fixture mapping",
                        now, counters);
                return FixtureResolution.blocked(NormalizationAnomalyCode.MAPPING_CONFLICT);
            }
            if (fixture.kickoff().equals(source.kickoff())
                    && fixture.status() == status
                    && fixture.phase().equals(competition.phase())) {
                return FixtureResolution.unchanged(fixture.id());
            }
            CanonicalFixture revised = fixture.revise(source.kickoff(), status, competition.phase(), now);
            catalogRepository.updateFixture(revised);
            return FixtureResolution.updated(fixture.id());
        }

        Optional<CanonicalFixture> exactFixture = catalogRepository.findFixture(
                competitionId, seasonId, homeTeamId, awayTeamId, source.kickoff());
        CanonicalFixture fixture;
        Change change;
        if (exactFixture.isPresent()) {
            fixture = exactFixture.get();
            change = Change.UNCHANGED;
        }
        else {
            fixture = new CanonicalFixture(
                    UUID.randomUUID(), competitionId, seasonId, homeTeamId, awayTeamId,
                    source.kickoff(), status, competition.phase(), now, now);
            catalogRepository.insertFixture(fixture);
            change = Change.CREATED;
        }
        mappingRepository.save(ProviderMapping.confirmed(
                provider,
                ProviderEntityType.FIXTURE,
                source.providerFixtureId(),
                fixture.id(),
                competition.season(),
                competition.phase(),
                now));
        return new FixtureResolution(fixture.id(), change, null);
    }

    private MappingResolution resolveMapping(
            UUID snapshotId,
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase,
            String sourceName,
            Instant now,
            Counters counters) {
        Optional<ProviderMapping> mapping = mappingRepository.find(
                provider, entityType, providerEntityId, season, phase);
        if (mapping.isEmpty()) {
            recordAnomaly(
                    snapshotId, provider, entityType, providerEntityId,
                    NormalizationAnomalyCode.MISSING_MAPPING,
                    "No explicit mapping for source name: " + sourceName, now, counters);
            return MappingResolution.blocked(NormalizationAnomalyCode.MISSING_MAPPING);
        }
        if (mapping.get().status() == MappingStatus.AMBIGUOUS) {
            recordAnomaly(
                    snapshotId, provider, entityType, providerEntityId,
                    NormalizationAnomalyCode.AMBIGUOUS_MAPPING,
                    "Ambiguous mapping remains pending for source name: " + sourceName, now, counters);
            return MappingResolution.blocked(NormalizationAnomalyCode.AMBIGUOUS_MAPPING);
        }
        if (mapping.get().status() == MappingStatus.REJECTED) {
            recordAnomaly(
                    snapshotId, provider, entityType, providerEntityId,
                    NormalizationAnomalyCode.REJECTED_MAPPING,
                    "Rejected mapping for source name: " + sourceName, now, counters);
            return MappingResolution.blocked(NormalizationAnomalyCode.REJECTED_MAPPING);
        }
        return MappingResolution.confirmed(mapping.get().canonicalEntityId());
    }

    private void insertObservation(
            UUID snapshotId,
            UUID canonicalFixtureId,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            ObservationStatus status,
            NormalizationAnomalyCode reason,
            Instant now) {
        DiscoveredCompetition competition = source.competition();
        catalogRepository.insertObservation(new FixtureObservation(
                UUID.randomUUID(),
                snapshotId,
                canonicalFixtureId,
                provider,
                source.providerFixtureId(),
                competition == null ? null : competition.providerCompetitionId(),
                source.homeTeam().providerTeamId(),
                source.awayTeam().providerTeamId(),
                source.kickoff(),
                source.status(),
                competition == null ? "" : competition.phase(),
                status,
                reason == null ? null : reason.name(),
                observedAt,
                now));
    }

    private void recordSnapshotAnomaly(
            UUID snapshotId,
            RawSnapshot rawSnapshot,
            NormalizationAnomalyCode code,
            String details) {
        anomalyRepository.save(NormalizationAnomaly.open(
                snapshotId,
                rawSnapshot.provider(),
                ProviderEntityType.SNAPSHOT,
                rawSnapshot.sha256(),
                code,
                details,
                clock.instant()));
    }

    private void recordAnomaly(
            UUID snapshotId,
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            NormalizationAnomalyCode code,
            String details,
            Instant now,
            Counters counters) {
        anomalyRepository.save(NormalizationAnomaly.open(
                snapshotId, provider, entityType, providerEntityId, code, details, now));
        counters.anomalies++;
    }

    private boolean hasCanonicalFields(
            DiscoveredCompetition competition,
            DiscoveredTeam homeTeam,
            DiscoveredTeam awayTeam) {
        return competition != null
                && hasText(competition.providerCompetitionId())
                && hasText(competition.name())
                && hasText(competition.countryCode())
                && hasText(competition.type())
                && hasText(competition.season())
                && hasText(competition.phase())
                && hasText(homeTeam.providerTeamId())
                && hasText(homeTeam.countryCode())
                && hasText(awayTeam.providerTeamId())
                && hasText(awayTeam.countryCode());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private NormalizationAnomalyCode firstFailure(MappingResolution... resolutions) {
        for (MappingResolution resolution : resolutions) {
            if (resolution.blockingCode() != null) {
                return resolution.blockingCode();
            }
        }
        return null;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

    private NormalizationResult failedResult(
            StoredSnapshot storedSnapshot,
            RawSnapshot rawSnapshot,
            int anomalies) {
        return new NormalizationResult(
                storedSnapshot.id(), rawSnapshot.sha256(), storedSnapshot.inserted(), false,
                0, 0, 0, 0, anomalies);
    }

    private record MappingResolution(UUID canonicalId, NormalizationAnomalyCode blockingCode) {

        static MappingResolution confirmed(UUID canonicalId) {
            return new MappingResolution(canonicalId, null);
        }

        static MappingResolution blocked(NormalizationAnomalyCode code) {
            return new MappingResolution(null, code);
        }
    }

    private record FixtureResolution(UUID fixtureId, Change change, NormalizationAnomalyCode blockingCode) {

        static FixtureResolution blocked(NormalizationAnomalyCode code) {
            return new FixtureResolution(null, null, code);
        }

        static FixtureResolution updated(UUID fixtureId) {
            return new FixtureResolution(fixtureId, Change.UPDATED, null);
        }

        static FixtureResolution unchanged(UUID fixtureId) {
            return new FixtureResolution(fixtureId, Change.UNCHANGED, null);
        }
    }

    private enum Change {
        CREATED,
        UPDATED,
        UNCHANGED
    }

    private static final class Counters {
        private int created;
        private int updated;
        private int unchanged;
        private int blocked;
        private int anomalies;
    }
}
