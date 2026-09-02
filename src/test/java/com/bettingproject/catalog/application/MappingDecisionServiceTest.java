package com.bettingproject.catalog.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bettingproject.catalog.domain.CanonicalCompetition;
import com.bettingproject.catalog.domain.CanonicalFixture;
import com.bettingproject.catalog.domain.CanonicalSeason;
import com.bettingproject.catalog.domain.CanonicalTeam;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.application.StoredProviderMapping;
import com.bettingproject.identity.domain.MappingStatus;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import com.bettingproject.identity.domain.ProviderMappingDecision;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MappingDecisionServiceTest {

    private static final Instant DECIDED_AT = Instant.parse("2026-09-01T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(DECIDED_AT, ZoneOffset.UTC);
    private static final ProviderMappingKey KEY = new ProviderMappingKey(
            "synthetic-provider", ProviderEntityType.TEAM, "synthetic-team-42", "", "");
    private static final UUID CANONICAL_TEAM_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000042");
    private static final UUID ANOMALY_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000043");

    private final RecordingMappingRepository mappingRepository = new RecordingMappingRepository();
    private final StubCatalogRepository catalogRepository = new StubCatalogRepository(
            Set.of(CANONICAL_TEAM_ID));
    private final RecordingDecisionJournal decisionJournal = new RecordingDecisionJournal();
    private final RecordingReceiptStore receiptStore = new RecordingReceiptStore();
    private final RecordingAnomalyStore anomalyStore = new RecordingAnomalyStore(
            List.of(ANOMALY_ID));
    private final List<String> lockOrder = new ArrayList<>();
    private OperatorIdentityResult operator = new OperatorIdentityResult.Available(
            new OperatorIdentity("test-operator"));
    private MappingDecisionService service;

    @BeforeEach
    void setUp() {
        service = new MappingDecisionService(
                mappingRepository,
                catalogRepository,
                decisionJournal,
                receiptStore,
                anomalyStore,
                () -> operator,
                ignored -> "Review complete; token=[REDACTED]",
                key -> lockOrder.add("idempotency:" + key),
                () -> lockOrder.add("calendar"),
                CLOCK);
    }

    @Test
    void confirmsAnAbsentMappingAndPersistsOneAuditedDecision() {
        MappingDecisionCommand command = confirmCommand(
                0L, "mapping-confirm-42", "Review complete; token=fake-value");

        MappingDecisionResult result = service.decide(command);

        assertThat(result).isInstanceOf(MappingDecisionResult.Applied.class);
        MappingDecisionResult.Applied applied = (MappingDecisionResult.Applied) result;
        ProviderMappingDecision decision = applied.decision();
        ProviderMapping mapping = mappingRepository.mappings.get(KEY);
        assertThat(lockOrder).containsExactly("idempotency:mapping-confirm-42", "calendar");
        assertThat(mapping.status()).isEqualTo(MappingStatus.CONFIRMED);
        assertThat(mapping.canonicalEntityId()).isEqualTo(CANONICAL_TEAM_ID);
        assertThat(mapping.version()).isEqualTo(1);
        assertThat(mapping.createdAt()).isEqualTo(DECIDED_AT);
        assertThat(mapping.updatedAt()).isEqualTo(DECIDED_AT);
        assertThat(decision.expectedVersion()).isZero();
        assertThat(decision.resultingVersion()).isEqualTo(1);
        assertThat(decision.operatorId()).isEqualTo("test-operator");
        assertThat(decision.justification()).isEqualTo("Review complete; token=[REDACTED]");
        assertThat(decision.createdAt()).isEqualTo(DECIDED_AT);
        assertThat(decision.resultingConfidence()).isEqualTo(1.0);
        assertThat(receiptStore.receipts).hasSize(1);
        assertThat(receiptStore.receipts.values().iterator().next().createdAt())
                .isEqualTo(DECIDED_AT);
        assertThat(receiptStore.receipts.values().iterator().next().commandSha256())
                .matches("[0-9a-f]{64}");
        assertThat(decisionJournal.decisions).containsOnlyKeys(decision.id());
        assertThat(applied.correlatedAnomalyIds()).containsExactly(ANOMALY_ID);
        assertThat(anomalyStore.correlated).containsEntry(decision.id(), List.of(ANOMALY_ID));
        assertThat(decision.toString()).doesNotContain("fake-value");
    }

    @Test
    void sameIdempotencyKeyAndContentReturnsTheOriginalDecisionBeforeOperatorLookup() {
        MappingDecisionCommand command = confirmCommand(
                0L, "mapping-confirm-idempotent", "Same review");
        MappingDecisionResult.Applied first = (MappingDecisionResult.Applied) service.decide(command);
        operator = new OperatorIdentityResult.Unavailable();

        MappingDecisionResult second = service.decide(command);

        assertThat(second).isInstanceOf(MappingDecisionResult.AlreadyApplied.class);
        assertThat(((MappingDecisionResult.AlreadyApplied) second).decision())
                .isEqualTo(first.decision());
        assertThat(mappingRepository.insertions).isEqualTo(1);
        assertThat(decisionJournal.decisions).hasSize(1);
        assertThat(lockOrder).containsExactly(
                "idempotency:mapping-confirm-idempotent",
                "calendar",
                "idempotency:mapping-confirm-idempotent");
    }

    @Test
    void reusedIdempotencyKeyWithDifferentContentConflictsWithoutMutation() {
        service.decide(confirmCommand(0L, "mapping-confirm-conflict", "Initial review"));

        MappingDecisionResult result = service.decide(
                confirmCommand(1L, "mapping-confirm-conflict", "Different review"));

        assertThat(result).isInstanceOf(MappingDecisionResult.IdempotencyConflict.class);
        assertThat(mappingRepository.mappings.get(KEY).version()).isEqualTo(1);
        assertThat(decisionJournal.decisions).hasSize(1);
        assertThat(receiptStore.receipts).hasSize(1);
    }

    @Test
    void identicalStateUnderANewKeyIsAuditedAndAdvancesTheVersion() {
        service.decide(confirmCommand(0L, "mapping-confirm-first", "First review"));

        MappingDecisionResult result = service.decide(
                confirmCommand(1L, "mapping-confirm-second", "Second review"));

        assertThat(result).isInstanceOf(MappingDecisionResult.Applied.class);
        MappingDecisionResult.Applied applied = (MappingDecisionResult.Applied) result;
        assertThat(mappingRepository.mappings.get(KEY).version()).isEqualTo(2);
        assertThat(applied.decision().previousStatus()).isEqualTo(MappingStatus.CONFIRMED);
        assertThat(applied.decision().resultingStatus()).isEqualTo(MappingStatus.CONFIRMED);
        assertThat(applied.decision().expectedVersion()).isEqualTo(1);
        assertThat(applied.decision().resultingVersion()).isEqualTo(2);
        assertThat(decisionJournal.decisions).hasSize(2);
        assertThat(receiptStore.receipts).hasSize(2);
    }

    @Test
    void staleVersionDoesNotCreateAReceiptOrHistoryEntry() {
        service.decide(confirmCommand(0L, "mapping-confirm-current", "Current review"));

        MappingDecisionResult result = service.decide(
                MappingDecisionCommand.reject(
                        KEY, 0L, "mapping-reject-stale", "Stale rejection"));

        assertThat(result).isEqualTo(new MappingDecisionResult.VersionConflict(0, 1L));
        assertThat(mappingRepository.mappings.get(KEY).status()).isEqualTo(MappingStatus.CONFIRMED);
        assertThat(mappingRepository.mappings.get(KEY).version()).isEqualTo(1);
        assertThat(decisionJournal.decisions).hasSize(1);
        assertThat(receiptStore.receipts).hasSize(1);
    }

    @Test
    void staleVersionTakesPrecedenceOverAMissingCanonicalTarget() {
        service.decide(confirmCommand(
                0L, "mapping-confirm-before-missing-target", "Current review"));
        UUID missingCanonicalTeamId = UUID.fromString(
                "00000000-0000-0000-0000-000000000099");

        MappingDecisionResult result = service.decide(MappingDecisionCommand.confirm(
                KEY,
                missingCanonicalTeamId,
                0L,
                "mapping-confirm-stale-missing-target",
                "Stale missing target review"));

        assertThat(result).isEqualTo(new MappingDecisionResult.VersionConflict(0, 1L));
        assertThat(mappingRepository.mappings.get(KEY).canonicalEntityId())
                .isEqualTo(CANONICAL_TEAM_ID);
        assertThat(mappingRepository.mappings.get(KEY).version()).isEqualTo(1);
        assertThat(decisionJournal.decisions).hasSize(1);
        assertThat(receiptStore.receipts).hasSize(1);
    }

    @Test
    void missingOperatorRefusesTheMutationAfterOnlyTheIdempotencyLock() {
        operator = new OperatorIdentityResult.Unavailable();

        MappingDecisionResult result = service.decide(
                MappingDecisionCommand.reject(
                        KEY, 0L, "mapping-reject-no-operator", "Reviewed rejection"));

        assertThat(result).isInstanceOf(MappingDecisionResult.OperatorUnavailable.class);
        assertThat(lockOrder).containsExactly("idempotency:mapping-reject-no-operator");
        assertThat(mappingRepository.mappings).isEmpty();
        assertThat(receiptStore.receipts).isEmpty();
        assertThat(decisionJournal.decisions).isEmpty();
    }

    @Test
    void createsAnAuditedRejectedMappingWithNoCanonicalEntityOrConfidence() {
        MappingDecisionResult result = service.decide(MappingDecisionCommand.reject(
                KEY, 0L, "mapping-reject-42", "Provider identity rejected"));

        assertThat(result).isInstanceOf(MappingDecisionResult.Applied.class);
        ProviderMapping mapping = mappingRepository.mappings.get(KEY);
        ProviderMappingDecision decision = ((MappingDecisionResult.Applied) result).decision();
        assertThat(mapping.status()).isEqualTo(MappingStatus.REJECTED);
        assertThat(mapping.canonicalEntityId()).isNull();
        assertThat(mapping.confidence()).isNull();
        assertThat(decision.resultingCanonicalEntityId()).isNull();
        assertThat(decision.resultingConfidence()).isNull();
    }

    @Test
    void missingCanonicalTargetIsRejectedAfterAValidCreationPrecondition() {
        UUID missingCanonicalTeamId = UUID.fromString(
                "00000000-0000-0000-0000-000000000098");

        MappingDecisionResult result = service.decide(MappingDecisionCommand.confirm(
                KEY,
                missingCanonicalTeamId,
                0L,
                "mapping-confirm-missing-target",
                "Missing target review"));

        assertThat(result).isEqualTo(new MappingDecisionResult.NotFound("canonical_entity"));
        assertThat(mappingRepository.mappings).isEmpty();
        assertThat(receiptStore.receipts).isEmpty();
        assertThat(decisionJournal.decisions).isEmpty();
    }

    @Test
    void canonicalTargetOfAnotherEntityTypeIsRejected() {
        ProviderMappingKey competitionKey = new ProviderMappingKey(
                "synthetic-provider",
                ProviderEntityType.COMPETITION,
                "synthetic-competition-42",
                "2026/2027",
                "REGULAR");

        MappingDecisionResult result = service.decide(MappingDecisionCommand.confirm(
                competitionKey,
                CANONICAL_TEAM_ID,
                0L,
                "mapping-confirm-incompatible-target",
                "Incompatible target review"));

        assertThat(result).isEqualTo(new MappingDecisionResult.NotFound("canonical_entity"));
        assertThat(mappingRepository.mappings).isEmpty();
        assertThat(receiptStore.receipts).isEmpty();
        assertThat(decisionJournal.decisions).isEmpty();
    }

    @Test
    void positiveExpectedVersionForAnAbsentMappingReturnsMappingNotFound() {
        MappingDecisionResult result = service.decide(confirmCommand(
                1L,
                "mapping-confirm-absent-versioned",
                "Absent mapping review"));

        assertThat(result).isEqualTo(new MappingDecisionResult.NotFound("provider_mapping"));
        assertThat(mappingRepository.mappings).isEmpty();
        assertThat(receiptStore.receipts).isEmpty();
        assertThat(decisionJournal.decisions).isEmpty();
    }

    @Test
    void confirmsAnExistingAmbiguousMappingAndAuditsItsPreviousState() {
        ProviderMapping ambiguous = ProviderMapping.ambiguous(
                KEY.provider(),
                KEY.entityType(),
                KEY.providerEntityId(),
                KEY.season(),
                KEY.phase(),
                0.5,
                DECIDED_AT.minusSeconds(60));
        mappingRepository.mappings.put(KEY, ambiguous);

        MappingDecisionResult result = service.decide(confirmCommand(
                1L,
                "mapping-confirm-ambiguous",
                "Ambiguous mapping resolved"));

        assertThat(result).isInstanceOf(MappingDecisionResult.Applied.class);
        ProviderMappingDecision decision = ((MappingDecisionResult.Applied) result).decision();
        assertThat(decision.previousStatus()).isEqualTo(MappingStatus.AMBIGUOUS);
        assertThat(decision.previousCanonicalEntityId()).isNull();
        assertThat(decision.previousConfidence()).isEqualTo(0.5);
        assertThat(decision.resultingStatus()).isEqualTo(MappingStatus.CONFIRMED);
        assertThat(decision.resultingCanonicalEntityId()).isEqualTo(CANONICAL_TEAM_ID);
        assertThat(decision.resultingVersion()).isEqualTo(2);
    }

    @Test
    void rejectsAnExistingConfirmedMappingAndAuditsItsPreviousState() {
        ProviderMapping confirmed = ProviderMapping.confirmed(
                KEY.provider(),
                KEY.entityType(),
                KEY.providerEntityId(),
                CANONICAL_TEAM_ID,
                KEY.season(),
                KEY.phase(),
                DECIDED_AT.minusSeconds(60));
        mappingRepository.mappings.put(KEY, confirmed);

        MappingDecisionResult result = service.decide(MappingDecisionCommand.reject(
                KEY,
                1L,
                "mapping-reject-confirmed",
                "Confirmed mapping rejected"));

        assertThat(result).isInstanceOf(MappingDecisionResult.Applied.class);
        ProviderMappingDecision decision = ((MappingDecisionResult.Applied) result).decision();
        assertThat(decision.previousStatus()).isEqualTo(MappingStatus.CONFIRMED);
        assertThat(decision.previousCanonicalEntityId()).isEqualTo(CANONICAL_TEAM_ID);
        assertThat(decision.previousConfidence()).isEqualTo(1.0);
        assertThat(decision.resultingStatus()).isEqualTo(MappingStatus.REJECTED);
        assertThat(decision.resultingCanonicalEntityId()).isNull();
        assertThat(decision.resultingConfidence()).isNull();
        assertThat(decision.resultingVersion()).isEqualTo(2);
    }

    private MappingDecisionCommand confirmCommand(
            long expectedVersion,
            String idempotencyKey,
            String justification) {
        return MappingDecisionCommand.confirm(
                KEY, CANONICAL_TEAM_ID, expectedVersion, idempotencyKey, justification);
    }

    private static final class RecordingMappingRepository implements ProviderMappingRepository {

        private final Map<ProviderMappingKey, ProviderMapping> mappings = new HashMap<>();
        private int insertions;

        @Override
        public Optional<ProviderMapping> find(ProviderMappingKey key) {
            return Optional.ofNullable(mappings.get(key));
        }

        @Override
        public StoredProviderMapping insertIfAbsentAndResolve(ProviderMapping mapping) {
            return insert(mapping);
        }

        @Override
        public Optional<ProviderMapping> findForUpdate(ProviderMappingKey key) {
            return find(key);
        }

        @Override
        public StoredProviderMapping insertForDecisionIfAbsent(ProviderMapping mapping) {
            return insert(mapping);
        }

        private StoredProviderMapping insert(ProviderMapping mapping) {
            ProviderMapping existing = mappings.putIfAbsent(mapping.key(), mapping);
            if (existing == null) {
                insertions++;
                return new StoredProviderMapping(mapping, true);
            }
            return new StoredProviderMapping(existing, false);
        }

        @Override
        public boolean updateIfVersion(ProviderMapping mapping, long expectedVersion) {
            ProviderMapping current = mappings.get(mapping.key());
            if (current == null || current.version() != expectedVersion) {
                return false;
            }
            mappings.put(mapping.key(), mapping);
            return true;
        }
    }

    private static final class RecordingReceiptStore implements ControlCommandReceiptStore {

        private final Map<String, ControlCommandReceipt> receipts = new LinkedHashMap<>();

        @Override
        public Optional<ControlCommandReceipt> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(receipts.get(idempotencyKey));
        }

        @Override
        public void insert(ControlCommandReceipt receipt) {
            receipts.put(receipt.idempotencyKey(), receipt);
        }
    }

    private static final class RecordingDecisionJournal
            implements ProviderMappingDecisionJournal {

        private final Map<UUID, ProviderMappingDecision> decisions = new LinkedHashMap<>();

        @Override
        public Optional<ProviderMappingDecision> find(UUID decisionId) {
            return Optional.ofNullable(decisions.get(decisionId));
        }

        @Override
        public void append(ProviderMappingDecision decision) {
            decisions.put(decision.id(), decision);
        }
    }

    private static final class RecordingAnomalyStore implements MappingDecisionAnomalyStore {

        private final List<UUID> openAnomalyIds;
        private final Map<UUID, List<UUID>> correlated = new HashMap<>();

        private RecordingAnomalyStore(List<UUID> openAnomalyIds) {
            this.openAnomalyIds = List.copyOf(openAnomalyIds);
        }

        @Override
        public List<MappingDecisionAnomalyReference> findOpenAnomalies(
                ProviderMappingKey mappingKey) {
            return openAnomalyIds.stream()
                    .map(anomalyId -> new MappingDecisionAnomalyReference(
                            anomalyId,
                            anomalyId))
                    .toList();
        }

        @Override
        public void correlate(UUID decisionId, List<UUID> anomalyIds, Instant createdAt) {
            assertThat(createdAt).isEqualTo(DECIDED_AT);
            correlated.put(decisionId, List.copyOf(anomalyIds));
        }

        @Override
        public List<UUID> findAnomalyIds(UUID decisionId) {
            return correlated.getOrDefault(decisionId, List.of());
        }

        @Override
        public List<MappingDecisionAnomalyReference> findAnomalies(UUID decisionId) {
            return findAnomalyIds(decisionId).stream()
                    .map(anomalyId -> new MappingDecisionAnomalyReference(
                            anomalyId,
                            anomalyId))
                    .toList();
        }
    }

    private static final class StubCatalogRepository implements CatalogRepository {

        private final Set<UUID> teamIds;

        private StubCatalogRepository(Set<UUID> teamIds) {
            this.teamIds = teamIds;
        }

        @Override
        public boolean existsCompetition(UUID id) {
            return false;
        }

        @Override
        public boolean existsTeam(UUID id) {
            return teamIds.contains(id);
        }

        @Override
        public boolean existsFixture(UUID id) {
            return false;
        }

        @Override
        public Optional<CanonicalCompetition> findCompetition(String name, String countryCode) {
            throw unsupported();
        }

        @Override
        public CanonicalCompetition getOrCreateCompetition(CanonicalCompetition competition) {
            throw unsupported();
        }

        @Override
        public Optional<CanonicalSeason> findSeason(UUID competitionId, String label) {
            throw unsupported();
        }

        @Override
        public CanonicalSeason getOrCreateSeason(CanonicalSeason season) {
            throw unsupported();
        }

        @Override
        public Optional<CanonicalTeam> findTeam(String name, String countryCode) {
            throw unsupported();
        }

        @Override
        public CanonicalTeam getOrCreateTeam(CanonicalTeam team) {
            throw unsupported();
        }

        @Override
        public Optional<CanonicalFixture> findFixture(UUID fixtureId) {
            throw unsupported();
        }

        @Override
        public Optional<CanonicalFixture> findFixtureForUpdate(UUID fixtureId) {
            throw unsupported();
        }

        @Override
        public Optional<CanonicalFixture> findFixture(
                UUID competitionId,
                UUID seasonId,
                UUID homeTeamId,
                UUID awayTeamId,
                Instant kickoff) {
            throw unsupported();
        }

        @Override
        public List<CanonicalFixture> findFixtureIdentityCandidatesForUpdate(
                UUID competitionId,
                UUID seasonId,
                UUID sourceHomeTeamId,
                UUID sourceAwayTeamId,
                Instant kickoff) {
            throw unsupported();
        }

        @Override
        public StoredCanonicalFixture insertOrResolveFixture(CanonicalFixture fixture) {
            throw unsupported();
        }

        @Override
        public boolean updateFixtureIfAuthorityMatches(
                CanonicalFixture fixture,
                UUID expectedAuthorityObservationId) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used by this unit test");
        }
    }
}
