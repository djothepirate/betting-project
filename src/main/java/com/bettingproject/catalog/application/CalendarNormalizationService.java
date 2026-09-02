package com.bettingproject.catalog.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.catalog.domain.CanonicalFixture;
import com.bettingproject.catalog.domain.CanonicalSeason;
import com.bettingproject.catalog.domain.CalendarAuthorityRole;
import com.bettingproject.catalog.domain.FixtureApplicationLog;
import com.bettingproject.catalog.domain.FixtureApplicationOutcome;
import com.bettingproject.catalog.domain.FixtureAuthorityStamp;
import com.bettingproject.catalog.domain.FixtureCanonicalFacts;
import com.bettingproject.catalog.domain.FixtureChronologyPolicy;
import com.bettingproject.catalog.domain.FixtureIdentityMatch;
import com.bettingproject.catalog.domain.FixtureIdentityPolicy;
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
import com.bettingproject.identity.application.NormalizationAnomalyLifecycleService;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.application.StoredProviderMapping;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.NormalizationAnomalyCode;
import com.bettingproject.identity.domain.NormalizationAnomalyKey;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Profile({"control-api", "batch-worker"})
public class CalendarNormalizationService {

    private final SnapshotStore snapshotStore;
    private final SnapshotParser<CalendarSnapshot> snapshotParser;
    private final CalendarNormalizationLock normalizationLock;
    private final CalendarAuthorityPolicy authorityPolicy;
    private final FixtureChronologyPolicy chronologyPolicy;
    private final FixtureIdentityPolicy identityPolicy;
    private final CatalogRepository catalogRepository;
    private final FixtureObservationStore observationStore;
    private final FixtureApplicationJournal applicationJournal;
    private final ProviderMappingRepository mappingRepository;
    private final NormalizationAnomalyLifecycleService anomalyLifecycle;
    private final Clock clock;

    public CalendarNormalizationService(
            SnapshotStore snapshotStore,
            SnapshotParser<CalendarSnapshot> snapshotParser,
            CalendarNormalizationLock normalizationLock,
            CalendarAuthorityPolicy authorityPolicy,
            CatalogRepository catalogRepository,
            FixtureObservationStore observationStore,
            FixtureApplicationJournal applicationJournal,
            ProviderMappingRepository mappingRepository,
            NormalizationAnomalyLifecycleService anomalyLifecycle,
            Clock clock) {
        this.snapshotStore = snapshotStore;
        this.snapshotParser = snapshotParser;
        this.normalizationLock = normalizationLock;
        this.authorityPolicy = authorityPolicy;
        this.chronologyPolicy = new FixtureChronologyPolicy();
        this.identityPolicy = new FixtureIdentityPolicy();
        this.catalogRepository = catalogRepository;
        this.observationStore = observationStore;
        this.applicationJournal = applicationJournal;
        this.mappingRepository = mappingRepository;
        this.anomalyLifecycle = anomalyLifecycle;
        this.clock = clock;
    }

    @Transactional
    public NormalizationResult normalize(RawSnapshot rawSnapshot) {
        return normalizeInCurrentTransaction(rawSnapshot);
    }

    NormalizationResult normalizeStoredWithinCurrentTransaction(RawSnapshot rawSnapshot) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Stored snapshot normalization requires an active transaction");
        }
        return normalizeInCurrentTransaction(rawSnapshot);
    }

    private NormalizationResult normalizeInCurrentTransaction(RawSnapshot rawSnapshot) {
        Objects.requireNonNull(rawSnapshot, "rawSnapshot");
        StoredSnapshot storedSnapshot = snapshotStore.storeAndResolve(rawSnapshot);
        AnomalyAssessment anomalyAssessment = new AnomalyAssessment();
        CalendarSnapshot calendarSnapshot;
        try {
            calendarSnapshot = snapshotParser.parse(rawSnapshot.payload());
        }
        catch (UnsupportedCalendarSchemaException ignored) {
            normalizationLock.acquire();
            recordSnapshotAnomaly(
                    storedSnapshot.id(), rawSnapshot, NormalizationAnomalyCode.UNSUPPORTED_SCHEMA,
                    "Unsupported calendar fixture schema", anomalyAssessment);
            return failedResult(storedSnapshot, rawSnapshot, 1);
        }
        catch (Exception ignored) {
            normalizationLock.acquire();
            recordSnapshotAnomaly(
                    storedSnapshot.id(), rawSnapshot, NormalizationAnomalyCode.INVALID_SNAPSHOT,
                    "Calendar snapshot payload is invalid", anomalyAssessment);
            return failedResult(storedSnapshot, rawSnapshot, 1);
        }

        if (!rawSnapshot.provider().equals(calendarSnapshot.provider())) {
            normalizationLock.acquire();
            recordSnapshotAnomaly(
                    storedSnapshot.id(), rawSnapshot, NormalizationAnomalyCode.INVALID_SNAPSHOT,
                    "Snapshot provider does not match payload provider", anomalyAssessment);
            return failedResult(storedSnapshot, rawSnapshot, 1);
        }
        if (!CalendarSnapshotSchemas.isNormalizable(calendarSnapshot.schemaVersion())) {
            normalizationLock.acquire();
            recordSnapshotAnomaly(
                    storedSnapshot.id(), rawSnapshot,
                    NormalizationAnomalyCode.LEGACY_SCHEMA_NOT_NORMALIZABLE,
                    "Schema " + calendarSnapshot.schemaVersion()
                            + " remains replayable but lacks canonical fields",
                    anomalyAssessment);
            return failedResult(storedSnapshot, rawSnapshot, 1);
        }

        normalizationLock.acquire();
        Counters counters = new Counters();
        Instant observedAt = calendarSnapshot.observedAt();
        for (DiscoveredFixture fixture : calendarSnapshot.fixtures()) {
            normalizeFixture(
                    storedSnapshot.id(),
                    calendarSnapshot.schemaVersion(),
                    calendarSnapshot.provider(),
                    observedAt,
                    fixture,
                    counters,
                    anomalyAssessment);
        }
        anomalyLifecycle.completeAssessment(
                storedSnapshot.id(), anomalyAssessment.encounteredIds(), clock.instant());
        return new NormalizationResult(
                storedSnapshot.id(), rawSnapshot.sha256(), storedSnapshot.inserted(), true,
                counters.created, counters.updated, counters.unchanged, counters.blocked, counters.anomalies);
    }

    private void normalizeFixture(
            UUID snapshotId,
            String schemaVersion,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            Counters counters,
            AnomalyAssessment anomalyAssessment) {
        Instant now = clock.instant();
        DiscoveredCompetition competition = source.competition();
        if (!hasCanonicalFields(competition, source.homeTeam(), source.awayTeam())) {
            UUID applicationLogId = recordApplication(
                    snapshotId, null, schemaVersion, provider, observedAt, source,
                    ObservationStatus.REJECTED, NormalizationAnomalyCode.INVALID_FIXTURE,
                    FixtureApplicationOutcome.REJECTED, null, null, now);
            recordAnomaly(
                    snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                    sourceSeason(source), sourcePhase(source),
                    NormalizationAnomalyCode.INVALID_FIXTURE,
                    "Schema " + schemaVersion
                            + " fixture requires competition, season, phase and provider entity identifiers",
                    applicationLogId, now, counters, anomalyAssessment);
            counters.blocked++;
            return;
        }

        FixtureStatus fixtureStatus;
        try {
            fixtureStatus = FixtureStatus.valueOf(source.status());
        }
        catch (IllegalArgumentException exception) {
            UUID applicationLogId = recordApplication(
                    snapshotId, null, schemaVersion, provider, observedAt, source,
                    ObservationStatus.REJECTED, NormalizationAnomalyCode.INVALID_FIXTURE,
                    FixtureApplicationOutcome.REJECTED, null, null, now);
            recordAnomaly(
                    snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                    sourceSeason(source), sourcePhase(source),
                    NormalizationAnomalyCode.INVALID_FIXTURE,
                    "Unsupported canonical fixture status: " + source.status(),
                    applicationLogId, now, counters, anomalyAssessment);
            counters.blocked++;
            return;
        }

        CalendarAuthorityResolution authority = authorityPolicy.resolve(new CalendarAuthorityKey(
                provider,
                competition.providerCompetitionId(),
                competition.season(),
                competition.phase(),
                CalendarAuthorityDataType.CALENDAR));
        if (authority.role() == CalendarAuthorityRole.UNASSIGNED) {
            UUID applicationLogId = recordApplication(
                    snapshotId, null, schemaVersion, provider, observedAt, source,
                    ObservationStatus.BLOCKED, NormalizationAnomalyCode.UNASSIGNED_AUTHORITY,
                    FixtureApplicationOutcome.UNASSIGNED, authority, null, now);
            recordAnomaly(
                    snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                    competition.season(), competition.phase(),
                    NormalizationAnomalyCode.UNASSIGNED_AUTHORITY,
                    "No exact calendar authority assignment exists for the source context",
                    applicationLogId, now, counters, anomalyAssessment);
            counters.blocked++;
            return;
        }

        MappingResolution competitionMapping = resolveMapping(
                provider, ProviderEntityType.COMPETITION,
                competition.providerCompetitionId(), competition.season(), competition.phase(),
                competition.name());
        MappingResolution homeMapping = resolveMapping(
                provider, ProviderEntityType.TEAM,
                source.homeTeam().providerTeamId(), "", "", source.homeTeam().name());
        MappingResolution awayMapping = resolveMapping(
                provider, ProviderEntityType.TEAM,
                source.awayTeam().providerTeamId(), "", "", source.awayTeam().name());

        NormalizationAnomalyCode blockingCode = firstFailure(competitionMapping, homeMapping, awayMapping);
        if (blockingCode != null) {
            UUID applicationLogId = recordApplication(
                    snapshotId, null, schemaVersion, provider, observedAt, source,
                    ObservationStatus.BLOCKED, blockingCode, FixtureApplicationOutcome.BLOCKED,
                    authority, null, now);
            recordPendingMappingAnomaly(
                    snapshotId, provider, competitionMapping, applicationLogId,
                    now, counters, anomalyAssessment);
            recordPendingMappingAnomaly(
                    snapshotId, provider, homeMapping, applicationLogId,
                    now, counters, anomalyAssessment);
            recordPendingMappingAnomaly(
                    snapshotId, provider, awayMapping, applicationLogId,
                    now, counters, anomalyAssessment);
            counters.blocked++;
            return;
        }
        if (homeMapping.canonicalId().equals(awayMapping.canonicalId())) {
            UUID applicationLogId = recordApplication(
                    snapshotId, null, schemaVersion, provider, observedAt, source,
                    ObservationStatus.REJECTED, NormalizationAnomalyCode.INVALID_FIXTURE,
                    FixtureApplicationOutcome.REJECTED, authority, null, now);
            recordAnomaly(
                    snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                    competition.season(), competition.phase(),
                    NormalizationAnomalyCode.INVALID_FIXTURE,
                    "Home and away provider mappings resolve to the same canonical team",
                    applicationLogId, now, counters, anomalyAssessment);
            counters.blocked++;
            return;
        }

        CanonicalSeason season;
        if (authority.role() == CalendarAuthorityRole.PRIMARY) {
            season = catalogRepository.getOrCreateSeason(new CanonicalSeason(
                    UUID.randomUUID(), competitionMapping.canonicalId(), competition.season(),
                    null, null, now, now));
        }
        else {
            Optional<CanonicalSeason> existingSeason = catalogRepository.findSeason(
                    competitionMapping.canonicalId(), competition.season());
            if (existingSeason.isEmpty()) {
                recordControlWithoutPrimary(
                        snapshotId, schemaVersion, provider, observedAt, source,
                        authority, null, now, counters, anomalyAssessment);
                return;
            }
            season = existingSeason.get();
        }

        resolveAndApplyFixture(
                snapshotId,
                schemaVersion,
                provider,
                observedAt,
                source,
                fixtureStatus,
                competitionMapping.canonicalId(),
                season.id(),
                homeMapping.canonicalId(),
                awayMapping.canonicalId(),
                authority,
                now,
                counters,
                anomalyAssessment);
    }

    private void resolveAndApplyFixture(
            UUID snapshotId,
            String schemaVersion,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            FixtureStatus status,
            UUID competitionId,
            UUID seasonId,
            UUID homeTeamId,
            UUID awayTeamId,
            CalendarAuthorityResolution authority,
            Instant now,
            Counters counters,
            AnomalyAssessment anomalyAssessment) {
        DiscoveredCompetition competition = source.competition();
        Optional<ProviderMapping> existingMapping = mappingRepository.find(
                provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                competition.season(), competition.phase());
        CanonicalFixture fixture;
        boolean created = false;
        if (existingMapping.isPresent()) {
            ProviderMapping mapping = existingMapping.get();
            if (mapping.status() != MappingStatus.CONFIRMED) {
                NormalizationAnomalyCode code = mapping.status() == MappingStatus.AMBIGUOUS
                        ? NormalizationAnomalyCode.AMBIGUOUS_MAPPING
                        : NormalizationAnomalyCode.REJECTED_MAPPING;
                UUID applicationLogId = recordApplication(
                        snapshotId, null, schemaVersion, provider, observedAt, source,
                        ObservationStatus.BLOCKED, code, FixtureApplicationOutcome.BLOCKED,
                        authority, null, now);
                recordAnomaly(
                        snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                        competition.season(), competition.phase(), code,
                        "Fixture mapping is " + mapping.status(), applicationLogId,
                        now, counters, anomalyAssessment);
                counters.blocked++;
                return;
            }
            Optional<CanonicalFixture> mappedFixture = catalogRepository.findFixtureForUpdate(
                    mapping.canonicalEntityId());
            if (mappedFixture.isEmpty()) {
                UUID applicationLogId = recordApplication(
                        snapshotId, null, schemaVersion, provider, observedAt, source,
                        ObservationStatus.BLOCKED, NormalizationAnomalyCode.MAPPING_CONFLICT,
                        FixtureApplicationOutcome.BLOCKED, authority, null, now);
                recordAnomaly(
                        snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                        competition.season(), competition.phase(),
                        NormalizationAnomalyCode.MAPPING_CONFLICT,
                        "Confirmed fixture mapping points to a missing canonical fixture",
                        applicationLogId, now, counters, anomalyAssessment);
                counters.blocked++;
                return;
            }
            fixture = mappedFixture.get();
            NormalizationAnomalyCode identityFailure = identityFailure(
                    fixture, competitionId, seasonId, homeTeamId, awayTeamId,
                    source.neutralVenue(), source.participantsUnordered());
            if (identityFailure != null) {
                blockFixture(
                        snapshotId, schemaVersion, provider, observedAt, source,
                        fixture, identityFailure,
                        identityFailure == NormalizationAnomalyCode.PARTICIPANT_ORDER_CONFLICT
                                ? "Source participant inversion is not explicitly unordered"
                                : "Source participants or competition conflict with the confirmed fixture mapping",
                        authority, now, counters, anomalyAssessment);
                return;
            }
        }
        else {
            List<CanonicalFixture> candidates = catalogRepository.findFixtureIdentityCandidatesForUpdate(
                    competitionId, seasonId, homeTeamId, awayTeamId, source.kickoff());
            if (source.participantsUnordered() && candidates.size() > 1) {
                blockFixture(
                        snapshotId, schemaVersion, provider, observedAt, source,
                        null, NormalizationAnomalyCode.AMBIGUOUS_FIXTURE_IDENTITY,
                        "Several canonical fixtures match the participant pair and kickoff",
                        authority, now, counters, anomalyAssessment);
                return;
            }
            Optional<CanonicalFixture> selectedCandidate = selectIdentityCandidate(
                    candidates, homeTeamId, awayTeamId, source.participantsUnordered());
            if (selectedCandidate.isPresent()) {
                fixture = selectedCandidate.get();
                NormalizationAnomalyCode identityFailure = identityFailure(
                        fixture, competitionId, seasonId, homeTeamId, awayTeamId,
                        source.neutralVenue(), source.participantsUnordered());
                if (identityFailure != null) {
                    blockFixture(
                            snapshotId, schemaVersion, provider, observedAt, source,
                            fixture, identityFailure,
                            identityFailure == NormalizationAnomalyCode.PARTICIPANT_ORDER_CONFLICT
                                    ? "Source participant inversion is not explicitly unordered"
                                    : "Source participants conflict with the canonical fixture identity",
                            authority, now, counters, anomalyAssessment);
                    return;
                }
            }
            else {
                if (authority.role() == CalendarAuthorityRole.CONTROL) {
                    recordControlWithoutPrimary(
                            snapshotId, schemaVersion, provider, observedAt, source,
                            authority, null, now, counters, anomalyAssessment);
                    return;
                }
                StoredCanonicalFixture storedFixture = catalogRepository.insertOrResolveFixture(
                        new CanonicalFixture(
                                UUID.randomUUID(), competitionId, seasonId, homeTeamId, awayTeamId,
                                source.neutralVenue(), source.participantsUnordered(),
                                source.kickoff(), status, competition.phase(), null, now, now));
                fixture = storedFixture.fixture();
                created = storedFixture.inserted();
                NormalizationAnomalyCode identityFailure = identityFailure(
                        fixture, competitionId, seasonId, homeTeamId, awayTeamId,
                        source.neutralVenue(), source.participantsUnordered());
                if (identityFailure != null) {
                    if (created) {
                        throw new IllegalStateException(
                                "Inserted canonical fixture resolved to a conflicting identity: "
                                        + fixture.id());
                    }
                    blockFixture(
                            snapshotId, schemaVersion, provider, observedAt, source,
                            fixture, identityFailure,
                            "Concurrent canonical fixture resolution returned a conflicting identity",
                            authority, now, counters, anomalyAssessment);
                    return;
                }
            }

            StoredProviderMapping storedMapping = mappingRepository.insertIfAbsentAndResolve(
                    ProviderMapping.confirmed(
                            provider,
                            ProviderEntityType.FIXTURE,
                            source.providerFixtureId(),
                            fixture.id(),
                            competition.season(),
                            competition.phase(),
                            now));
            if (!isCompatibleFixtureMapping(storedMapping.mapping(), fixture.id())) {
                if (created) {
                    throw new IllegalStateException(
                            "Inserted canonical fixture cannot be committed with an incompatible mapping: "
                                    + fixture.id());
                }
                blockFixture(
                        snapshotId, schemaVersion, provider, observedAt, source,
                        fixture, NormalizationAnomalyCode.MAPPING_CONFLICT,
                        "Concurrent fixture mapping resolution returned a different decision",
                        authority, now, counters, anomalyAssessment);
                return;
            }
        }

        FixtureCanonicalFacts candidateFacts = new FixtureCanonicalFacts(
                fixture.competitionId(),
                fixture.seasonId(),
                fixture.homeTeamId(),
                fixture.awayTeamId(),
                source.neutralVenue(),
                source.participantsUnordered(),
                source.kickoff(),
                status,
                competition.phase());
        if (created) {
            createFromPrimary(
                    snapshotId, schemaVersion, provider, observedAt, source,
                    fixture, candidateFacts, authority, now, counters);
            return;
        }
        if (authority.role() == CalendarAuthorityRole.CONTROL) {
            applyControl(
                    snapshotId, schemaVersion, provider, observedAt, source,
                    fixture, candidateFacts, authority, now, counters, anomalyAssessment);
            return;
        }
        applyPrimary(
                snapshotId, schemaVersion, provider, observedAt, source,
                fixture, candidateFacts, authority, now, counters, anomalyAssessment);
    }

    private void createFromPrimary(
            UUID snapshotId,
            String schemaVersion,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            CanonicalFixture fixture,
            FixtureCanonicalFacts candidateFacts,
            CalendarAuthorityResolution authority,
            Instant now,
            Counters counters) {
        StoredFixtureObservation observation = storeObservation(
                snapshotId, fixture.id(), schemaVersion, provider, observedAt, source,
                ObservationStatus.NORMALIZED, null, now);
        FixtureAuthorityStamp stamp = new FixtureAuthorityStamp(
                observation.id(), observedAt, provider, authority.policyVersion());
        CanonicalFixture applied = fixture.apply(candidateFacts, stamp, now);
        updateFixtureAuthority(applied, null);
        appendApplication(
                observation.id(), fixture.id(), null,
                FixtureApplicationOutcome.CREATED, null, authority, now);
        counters.created++;
    }

    private void applyControl(
            UUID snapshotId,
            String schemaVersion,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            CanonicalFixture fixture,
            FixtureCanonicalFacts candidateFacts,
            CalendarAuthorityResolution authority,
            Instant now,
            Counters counters,
            AnomalyAssessment anomalyAssessment) {
        UUID previousAuthorityObservationId = previousAuthorityObservationId(fixture);
        if (FixtureCanonicalFacts.from(fixture).equals(candidateFacts)) {
            recordApplication(
                    snapshotId, fixture.id(), schemaVersion, provider, observedAt, source,
                    ObservationStatus.NORMALIZED, null, FixtureApplicationOutcome.UNCHANGED,
                    authority, previousAuthorityObservationId, now);
            counters.unchanged++;
            return;
        }

        UUID applicationLogId = recordApplication(
                snapshotId, fixture.id(), schemaVersion, provider, observedAt, source,
                ObservationStatus.BLOCKED, NormalizationAnomalyCode.CONTROL_DIVERGENCE,
                FixtureApplicationOutcome.CONTROL_DIVERGENCE,
                authority, previousAuthorityObservationId, now);
        recordAnomaly(
                snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                sourceSeason(source), sourcePhase(source),
                NormalizationAnomalyCode.CONTROL_DIVERGENCE,
                "Control observation contradicts the canonical fixture facts",
                applicationLogId, now, counters, anomalyAssessment);
        counters.blocked++;
    }

    private void applyPrimary(
            UUID snapshotId,
            String schemaVersion,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            CanonicalFixture fixture,
            FixtureCanonicalFacts candidateFacts,
            CalendarAuthorityResolution authority,
            Instant now,
            Counters counters,
            AnomalyAssessment anomalyAssessment) {
        FixtureAuthorityStamp previousAuthority = fixture.lastAuthority();
        UUID previousAuthorityObservationId = previousAuthorityObservationId(fixture);
        FixtureApplicationOutcome outcome = chronologyPolicy.evaluate(
                previousAuthority == null ? null : previousAuthority.observedAt(),
                FixtureCanonicalFacts.from(fixture),
                observedAt,
                candidateFacts);

        switch (outcome) {
            case STALE -> {
                recordApplication(
                        snapshotId, fixture.id(), schemaVersion, provider, observedAt, source,
                        ObservationStatus.BLOCKED, NormalizationAnomalyCode.STALE_OBSERVATION,
                        FixtureApplicationOutcome.STALE,
                        authority, previousAuthorityObservationId, now);
                counters.blocked++;
            }
            case EQUAL_AUTHORITY_TIME_CONFLICT -> recordPolicyFailure(
                    snapshotId, schemaVersion, provider, observedAt, source, fixture,
                    NormalizationAnomalyCode.EQUAL_AUTHORITY_TIME_CONFLICT,
                    FixtureApplicationOutcome.EQUAL_AUTHORITY_TIME_CONFLICT,
                    "Observation contradicts canonical facts at the same authority time",
                    authority, previousAuthorityObservationId, now, counters, anomalyAssessment);
            case INVALID_TRANSITION -> recordPolicyFailure(
                    snapshotId, schemaVersion, provider, observedAt, source, fixture,
                    NormalizationAnomalyCode.INVALID_TRANSITION,
                    FixtureApplicationOutcome.INVALID_TRANSITION,
                    "Observation requests a forbidden canonical fixture status transition",
                    authority, previousAuthorityObservationId, now, counters, anomalyAssessment);
            case UPDATED -> {
                StoredFixtureObservation observation = storeObservation(
                        snapshotId, fixture.id(), schemaVersion, provider, observedAt, source,
                        ObservationStatus.NORMALIZED, null, now);
                FixtureAuthorityStamp stamp = new FixtureAuthorityStamp(
                        observation.id(), observedAt, provider, authority.policyVersion());
                updateFixtureAuthority(
                        fixture.apply(candidateFacts, stamp, now),
                        previousAuthorityObservationId);
                appendApplication(
                        observation.id(), fixture.id(), previousAuthorityObservationId,
                        FixtureApplicationOutcome.UPDATED, null, authority, now);
                counters.updated++;
            }
            case UNCHANGED -> {
                StoredFixtureObservation observation = storeObservation(
                        snapshotId, fixture.id(), schemaVersion, provider, observedAt, source,
                        ObservationStatus.NORMALIZED, null, now);
                if (previousAuthority == null || observedAt.isAfter(previousAuthority.observedAt())) {
                    FixtureAuthorityStamp stamp = new FixtureAuthorityStamp(
                            observation.id(), observedAt, provider, authority.policyVersion());
                    updateFixtureAuthority(
                            fixture.advanceAuthority(stamp, now),
                            previousAuthorityObservationId);
                }
                appendApplication(
                        observation.id(), fixture.id(), previousAuthorityObservationId,
                        FixtureApplicationOutcome.UNCHANGED, null, authority, now);
                counters.unchanged++;
            }
            default -> throw new IllegalStateException(
                    "Unsupported PRIMARY chronology outcome: " + outcome);
        }
    }

    private void updateFixtureAuthority(
            CanonicalFixture fixture,
            UUID expectedAuthorityObservationId) {
        if (!catalogRepository.updateFixtureIfAuthorityMatches(
                fixture, expectedAuthorityObservationId)) {
            throw new IllegalStateException(
                    "Canonical fixture authority changed concurrently: " + fixture.id());
        }
    }

    private void recordPolicyFailure(
            UUID snapshotId,
            String schemaVersion,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            CanonicalFixture fixture,
            NormalizationAnomalyCode reason,
            FixtureApplicationOutcome outcome,
            String details,
            CalendarAuthorityResolution authority,
            UUID previousAuthorityObservationId,
            Instant now,
            Counters counters,
            AnomalyAssessment anomalyAssessment) {
        UUID applicationLogId = recordApplication(
                snapshotId, fixture.id(), schemaVersion, provider, observedAt, source,
                ObservationStatus.BLOCKED, reason, outcome,
                authority, previousAuthorityObservationId, now);
        recordAnomaly(
                snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                sourceSeason(source), sourcePhase(source), reason, details,
                applicationLogId, now, counters, anomalyAssessment);
        counters.blocked++;
    }

    private void blockFixture(
            UUID snapshotId,
            String schemaVersion,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            CanonicalFixture fixture,
            NormalizationAnomalyCode reason,
            String details,
            CalendarAuthorityResolution authority,
            Instant now,
            Counters counters,
            AnomalyAssessment anomalyAssessment) {
        UUID canonicalFixtureId = fixture == null ? null : fixture.id();
        UUID applicationLogId = recordApplication(
                snapshotId, canonicalFixtureId, schemaVersion, provider, observedAt, source,
                ObservationStatus.BLOCKED, reason, FixtureApplicationOutcome.BLOCKED,
                authority, fixture == null ? null : previousAuthorityObservationId(fixture), now);
        recordAnomaly(
                snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                sourceSeason(source), sourcePhase(source), reason, details,
                applicationLogId, now, counters, anomalyAssessment);
        counters.blocked++;
    }

    private void recordControlWithoutPrimary(
            UUID snapshotId,
            String schemaVersion,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            CalendarAuthorityResolution authority,
            CanonicalFixture fixture,
            Instant now,
            Counters counters,
            AnomalyAssessment anomalyAssessment) {
        UUID applicationLogId = recordApplication(
                snapshotId, fixture == null ? null : fixture.id(), schemaVersion,
                provider, observedAt, source,
                ObservationStatus.BLOCKED, NormalizationAnomalyCode.CONTROL_WITHOUT_PRIMARY,
                FixtureApplicationOutcome.BLOCKED, authority,
                fixture == null ? null : previousAuthorityObservationId(fixture), now);
        recordAnomaly(
                snapshotId, provider, ProviderEntityType.FIXTURE, source.providerFixtureId(),
                sourceSeason(source), sourcePhase(source),
                NormalizationAnomalyCode.CONTROL_WITHOUT_PRIMARY,
                "Control observation has no canonical primary fixture to compare",
                applicationLogId, now, counters, anomalyAssessment);
        counters.blocked++;
    }

    private NormalizationAnomalyCode identityFailure(
            CanonicalFixture fixture,
            UUID competitionId,
            UUID seasonId,
            UUID sourceHomeTeamId,
            UUID sourceAwayTeamId,
            Boolean sourceNeutralVenue,
            boolean sourceParticipantsUnordered) {
        if (!fixture.competitionId().equals(competitionId)
                || !fixture.seasonId().equals(seasonId)) {
            return NormalizationAnomalyCode.MAPPING_CONFLICT;
        }
        FixtureIdentityMatch match = identityPolicy.match(
                fixture.homeTeamId(), fixture.awayTeamId(),
                sourceHomeTeamId, sourceAwayTeamId,
                sourceNeutralVenue, sourceParticipantsUnordered);
        if (match != FixtureIdentityMatch.NO_MATCH) {
            return null;
        }
        boolean reversed = fixture.homeTeamId().equals(sourceAwayTeamId)
                && fixture.awayTeamId().equals(sourceHomeTeamId);
        return reversed
                ? NormalizationAnomalyCode.PARTICIPANT_ORDER_CONFLICT
                : NormalizationAnomalyCode.MAPPING_CONFLICT;
    }

    private Optional<CanonicalFixture> selectIdentityCandidate(
            List<CanonicalFixture> candidates,
            UUID sourceHomeTeamId,
            UUID sourceAwayTeamId,
            boolean sourceParticipantsUnordered) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (sourceParticipantsUnordered) {
            return Optional.of(candidates.get(0));
        }
        return candidates.stream()
                .filter(candidate -> candidate.homeTeamId().equals(sourceHomeTeamId)
                        && candidate.awayTeamId().equals(sourceAwayTeamId))
                .findFirst()
                .or(() -> Optional.of(candidates.get(0)));
    }

    private boolean isCompatibleFixtureMapping(
            ProviderMapping mapping,
            UUID canonicalFixtureId) {
        return mapping.status() == MappingStatus.CONFIRMED
                && canonicalFixtureId.equals(mapping.canonicalEntityId());
    }

    private UUID previousAuthorityObservationId(CanonicalFixture fixture) {
        return fixture.lastAuthority() == null
                ? null
                : fixture.lastAuthority().observationId();
    }

    private MappingResolution resolveMapping(
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase,
            String sourceName) {
        Optional<ProviderMapping> mapping = mappingRepository.find(
                provider, entityType, providerEntityId, season, phase);
        if (mapping.isEmpty()) {
            return MappingResolution.blocked(new PendingAnomaly(
                    entityType, providerEntityId, season, phase,
                    NormalizationAnomalyCode.MISSING_MAPPING,
                    "No explicit mapping for source name: " + sourceName));
        }
        if (mapping.get().status() == MappingStatus.AMBIGUOUS) {
            return MappingResolution.blocked(new PendingAnomaly(
                    entityType, providerEntityId, season, phase,
                    NormalizationAnomalyCode.AMBIGUOUS_MAPPING,
                    "Ambiguous mapping remains pending for source name: " + sourceName));
        }
        if (mapping.get().status() == MappingStatus.REJECTED) {
            return MappingResolution.blocked(new PendingAnomaly(
                    entityType, providerEntityId, season, phase,
                    NormalizationAnomalyCode.REJECTED_MAPPING,
                    "Rejected mapping for source name: " + sourceName));
        }
        return MappingResolution.confirmed(mapping.get().canonicalEntityId());
    }

    private UUID recordApplication(
            UUID snapshotId,
            UUID canonicalFixtureId,
            String schemaVersion,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            ObservationStatus status,
            NormalizationAnomalyCode reason,
            FixtureApplicationOutcome outcome,
            CalendarAuthorityResolution authority,
            UUID previousAuthorityObservationId,
            Instant now) {
        StoredFixtureObservation storedObservation = storeObservation(
                snapshotId, canonicalFixtureId, schemaVersion, provider, observedAt, source,
                status, reason, now);
        return appendApplication(
                storedObservation.id(), canonicalFixtureId, previousAuthorityObservationId,
                outcome, reason, authority, now);
    }

    private StoredFixtureObservation storeObservation(
            UUID snapshotId,
            UUID canonicalFixtureId,
            String schemaVersion,
            String provider,
            Instant observedAt,
            DiscoveredFixture source,
            ObservationStatus status,
            NormalizationAnomalyCode reason,
            Instant now) {
        DiscoveredCompetition competition = source.competition();
        return observationStore.storeAndResolve(new FixtureObservation(
                UUID.randomUUID(),
                snapshotId,
                canonicalFixtureId,
                provider,
                source.providerFixtureId(),
                competition == null ? null : competition.providerCompetitionId(),
                source.homeTeam().providerTeamId(),
                source.awayTeam().providerTeamId(),
                schemaVersion,
                competition == null ? null : competition.season(),
                source.neutralVenue(),
                source.participantsUnordered(),
                source.kickoff(),
                source.status(),
                competition == null ? "" : competition.phase(),
                status,
                reason == null ? null : reason.name(),
                observedAt,
                now));
    }

    private UUID appendApplication(
            UUID observationId,
            UUID canonicalFixtureId,
            UUID previousAuthorityObservationId,
            FixtureApplicationOutcome outcome,
            NormalizationAnomalyCode reason,
            CalendarAuthorityResolution authority,
            Instant now) {
        UUID applicationLogId = UUID.randomUUID();
        applicationJournal.append(new FixtureApplicationLog(
                applicationLogId,
                observationId,
                canonicalFixtureId,
                previousAuthorityObservationId,
                outcome,
                reason == null ? null : reason.name(),
                authority == null ? null : authority.role(),
                authority == null ? null : authority.policyVersion(),
                now));
        return applicationLogId;
    }

    private void recordSnapshotAnomaly(
            UUID snapshotId,
            RawSnapshot rawSnapshot,
            NormalizationAnomalyCode code,
            String details,
            AnomalyAssessment anomalyAssessment) {
        UUID anomalyId = anomalyLifecycle.recordOccurrence(
                new NormalizationAnomalyKey(
                        snapshotId, rawSnapshot.provider(), ProviderEntityType.SNAPSHOT,
                        rawSnapshot.sha256(), null, null, code),
                details, null, clock.instant());
        anomalyAssessment.encounter(anomalyId);
    }

    private void recordAnomaly(
            UUID snapshotId,
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase,
            NormalizationAnomalyCode code,
            String details,
            UUID fixtureApplicationLogId,
            Instant now,
            Counters counters,
            AnomalyAssessment anomalyAssessment) {
        UUID anomalyId = anomalyLifecycle.recordOccurrence(
                new NormalizationAnomalyKey(
                        snapshotId, provider, entityType, providerEntityId, season, phase, code),
                details, fixtureApplicationLogId, now);
        anomalyAssessment.encounter(anomalyId);
        counters.anomalies++;
    }

    private void recordPendingMappingAnomaly(
            UUID snapshotId,
            String provider,
            MappingResolution resolution,
            UUID fixtureApplicationLogId,
            Instant now,
            Counters counters,
            AnomalyAssessment anomalyAssessment) {
        PendingAnomaly pending = resolution.pendingAnomaly();
        if (pending == null) {
            return;
        }
        recordAnomaly(
                snapshotId, provider, pending.entityType(), pending.providerEntityId(),
                pending.season(), pending.phase(), pending.code(), pending.details(),
                fixtureApplicationLogId, now, counters, anomalyAssessment);
    }

    private String sourceSeason(DiscoveredFixture source) {
        return source.competition() == null ? null : source.competition().season();
    }

    private String sourcePhase(DiscoveredFixture source) {
        return source.competition() == null ? null : source.competition().phase();
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
            if (resolution.pendingAnomaly() != null) {
                return resolution.pendingAnomaly().code();
            }
        }
        return null;
    }

    private NormalizationResult failedResult(
            StoredSnapshot storedSnapshot,
            RawSnapshot rawSnapshot,
            int anomalies) {
        return new NormalizationResult(
                storedSnapshot.id(), rawSnapshot.sha256(), storedSnapshot.inserted(), false,
                0, 0, 0, 0, anomalies);
    }

    private record MappingResolution(UUID canonicalId, PendingAnomaly pendingAnomaly) {

        static MappingResolution confirmed(UUID canonicalId) {
            return new MappingResolution(canonicalId, null);
        }

        static MappingResolution blocked(PendingAnomaly pendingAnomaly) {
            return new MappingResolution(null, Objects.requireNonNull(pendingAnomaly));
        }
    }

    private record PendingAnomaly(
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase,
            NormalizationAnomalyCode code,
            String details) {
    }

    private static final class AnomalyAssessment {
        private final Set<UUID> encounteredIds = new HashSet<>();

        void encounter(UUID anomalyId) {
            encounteredIds.add(Objects.requireNonNull(anomalyId));
        }

        Set<UUID> encounteredIds() {
            return Set.copyOf(encounteredIds);
        }
    }

    private static final class Counters {
        private int created;
        private int updated;
        private int unchanged;
        private int blocked;
        private int anomalies;
    }
}
