package com.bettingproject.catalog.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.application.StoredProviderMapping;
import com.bettingproject.identity.domain.MappingDecisionType;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import com.bettingproject.identity.domain.ProviderMappingDecision;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("control-api")
public class MappingDecisionService {

    private final ProviderMappingRepository mappingRepository;
    private final CatalogRepository catalogRepository;
    private final ProviderMappingDecisionJournal decisionJournal;
    private final ControlCommandReceiptStore receiptStore;
    private final MappingDecisionAnomalyStore anomalyStore;
    private final OperatorIdentityProvider operatorIdentityProvider;
    private final DecisionJustificationSanitizer justificationSanitizer;
    private final ControlCommandLock controlCommandLock;
    private final CalendarNormalizationLock calendarNormalizationLock;
    private final MappingDecisionReplayPlanner replayPlanner;
    private final Clock clock;

    @Autowired
    public MappingDecisionService(
            ProviderMappingRepository mappingRepository,
            CatalogRepository catalogRepository,
            ProviderMappingDecisionJournal decisionJournal,
            ControlCommandReceiptStore receiptStore,
            MappingDecisionAnomalyStore anomalyStore,
            OperatorIdentityProvider operatorIdentityProvider,
            DecisionJustificationSanitizer justificationSanitizer,
            ControlCommandLock controlCommandLock,
            CalendarNormalizationLock calendarNormalizationLock,
            MappingDecisionReplayPlanner replayPlanner,
            Clock clock) {
        this.mappingRepository = mappingRepository;
        this.catalogRepository = catalogRepository;
        this.decisionJournal = decisionJournal;
        this.receiptStore = receiptStore;
        this.anomalyStore = anomalyStore;
        this.operatorIdentityProvider = operatorIdentityProvider;
        this.justificationSanitizer = justificationSanitizer;
        this.controlCommandLock = controlCommandLock;
        this.calendarNormalizationLock = calendarNormalizationLock;
        this.replayPlanner = replayPlanner;
        this.clock = clock;
    }

    MappingDecisionService(
            ProviderMappingRepository mappingRepository,
            CatalogRepository catalogRepository,
            ProviderMappingDecisionJournal decisionJournal,
            ControlCommandReceiptStore receiptStore,
            MappingDecisionAnomalyStore anomalyStore,
            OperatorIdentityProvider operatorIdentityProvider,
            DecisionJustificationSanitizer justificationSanitizer,
            ControlCommandLock controlCommandLock,
            CalendarNormalizationLock calendarNormalizationLock,
            Clock clock) {
        this(
                mappingRepository,
                catalogRepository,
                decisionJournal,
                receiptStore,
                anomalyStore,
                operatorIdentityProvider,
                justificationSanitizer,
                controlCommandLock,
                calendarNormalizationLock,
                new MappingDecisionReplayPlanner() {
                    @Override
                    public List<UUID> createRequests(
                            ProviderMappingDecision decision,
                            List<MappingDecisionAnomalyReference> correlatedAnomalies,
                            Instant createdAt) {
                        return List.of();
                    }

                    @Override
                    public List<UUID> findRequestIds(UUID decisionId) {
                        return List.of();
                    }
                },
                clock);
    }

    @Transactional
    public MappingDecisionResult decide(MappingDecisionCommand command) {
        ValidatedCommand validated = validate(command);
        if (validated == null) {
            return new MappingDecisionResult.Invalid("INVALID_MAPPING_DECISION");
        }

        controlCommandLock.acquire(validated.idempotencyKey());
        Optional<ControlCommandReceipt> existingReceipt = receiptStore.findByIdempotencyKey(
                validated.idempotencyKey());
        if (existingReceipt.isPresent()) {
            return resolveIdempotentResult(existingReceipt.get(), validated);
        }

        OperatorIdentityResult operatorResult = operatorIdentityProvider.currentOperator();
        if (!(operatorResult instanceof OperatorIdentityResult.Available availableOperator)) {
            return new MappingDecisionResult.OperatorUnavailable();
        }

        calendarNormalizationLock.acquire();
        Optional<ProviderMapping> current = mappingRepository.findForUpdate(
                validated.mappingKey());
        if (validated.expectedVersion() == 0 && current.isPresent()) {
            return new MappingDecisionResult.VersionConflict(
                    0, current.get().version());
        }
        if (validated.expectedVersion() > 0 && current.isEmpty()) {
            return new MappingDecisionResult.NotFound("provider_mapping");
        }
        if (validated.expectedVersion() > 0
                && current.orElseThrow().version() != validated.expectedVersion()) {
            return new MappingDecisionResult.VersionConflict(
                    validated.expectedVersion(), current.orElseThrow().version());
        }
        if (validated.decisionType() == MappingDecisionType.CONFIRM
                && !canonicalEntityExists(
                        validated.mappingKey().entityType(), validated.canonicalEntityId())) {
            return new MappingDecisionResult.NotFound("canonical_entity");
        }

        Instant decidedAt = clock.instant();
        ProviderMapping resulting;
        ProviderMapping previous = null;
        if (validated.expectedVersion() == 0) {
            ProviderMapping proposed = newMapping(validated, decidedAt);
            StoredProviderMapping stored = mappingRepository.insertForDecisionIfAbsent(proposed);
            if (!stored.inserted()) {
                return new MappingDecisionResult.VersionConflict(
                        0, stored.mapping().version());
            }
            resulting = stored.mapping();
        }
        else {
            previous = current.orElseThrow();
            resulting = revisedMapping(previous, validated, decidedAt);
            if (!mappingRepository.updateIfVersion(
                    resulting, validated.expectedVersion())) {
                Long actualVersion = mappingRepository.find(validated.mappingKey())
                        .map(ProviderMapping::version)
                        .orElse(null);
                return new MappingDecisionResult.VersionConflict(
                        validated.expectedVersion(), actualVersion);
            }
        }

        UUID decisionId = UUID.randomUUID();
        ControlCommandReceipt receipt = new ControlCommandReceipt(
                UUID.randomUUID(),
                validated.idempotencyKey(),
                commandType(validated.decisionType()),
                validated.commandSha256(),
                decisionId,
                decidedAt);
        ProviderMappingDecision decision = toDecision(
                decisionId,
                receipt.id(),
                previous,
                resulting,
                validated,
                availableOperator.identity(),
                decidedAt);

        receiptStore.insert(receipt);
        decisionJournal.append(decision);
        List<MappingDecisionAnomalyReference> anomalies = anomalyStore.findOpenAnomalies(
                validated.mappingKey());
        List<UUID> anomalyIds = anomalies.stream()
                .map(MappingDecisionAnomalyReference::anomalyId)
                .toList();
        anomalyStore.correlate(decision.id(), anomalyIds, decidedAt);
        List<UUID> replayRequestIds = replayPlanner.createRequests(
                decision, anomalies, decidedAt);
        return new MappingDecisionResult.Applied(
                decision, anomalyIds, replayRequestIds);
    }

    private MappingDecisionResult resolveIdempotentResult(
            ControlCommandReceipt receipt,
            ValidatedCommand command) {
        if (receipt.commandType() != commandType(command.decisionType())
                || !receipt.commandSha256().equals(command.commandSha256())) {
            return new MappingDecisionResult.IdempotencyConflict();
        }
        ProviderMappingDecision decision = decisionJournal.find(receipt.resultResourceId())
                .orElseThrow(() -> new IllegalStateException(
                        "Control command receipt points to a missing mapping decision"));
        List<UUID> anomalyIds = anomalyStore.findAnomalyIds(decision.id());
        return new MappingDecisionResult.AlreadyApplied(
                decision, anomalyIds, replayPlanner.findRequestIds(decision.id()));
    }

    private ValidatedCommand validate(MappingDecisionCommand command) {
        if (command == null
                || command.mappingKey() == null
                || command.decisionType() == null
                || command.expectedVersion() == null
                || command.expectedVersion() < 0) {
            return null;
        }
        if ((command.decisionType() == MappingDecisionType.CONFIRM
                && command.canonicalEntityId() == null)
                || (command.decisionType() == MappingDecisionType.REJECT
                && command.canonicalEntityId() != null)) {
            return null;
        }

        String idempotencyKey;
        try {
            idempotencyKey = ControlCommandReceipt.requireVisibleAscii(command.idempotencyKey());
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
        if (command.justification() == null) {
            return null;
        }
        String rawJustification = command.justification().strip();
        if (rawJustification.isEmpty() || rawJustification.length() > 1_000) {
            return null;
        }

        String sanitizedJustification;
        try {
            sanitizedJustification = justificationSanitizer.sanitize(rawJustification);
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
        if (sanitizedJustification == null) {
            return null;
        }
        sanitizedJustification = sanitizedJustification.strip();
        if (sanitizedJustification.isEmpty()
                || sanitizedJustification.length() > 1_000
                || sanitizedJustification.codePoints().anyMatch(Character::isISOControl)) {
            return null;
        }

        return new ValidatedCommand(
                command.mappingKey(),
                command.decisionType(),
                command.canonicalEntityId(),
                command.expectedVersion(),
                idempotencyKey,
                sanitizedJustification,
                commandSha256(command, rawJustification));
    }

    private ProviderMapping newMapping(ValidatedCommand command, Instant now) {
        ProviderMappingKey key = command.mappingKey();
        if (command.decisionType() == MappingDecisionType.CONFIRM) {
            return ProviderMapping.confirmed(
                    key.provider(), key.entityType(), key.providerEntityId(),
                    command.canonicalEntityId(), key.season(), key.phase(), now);
        }
        return ProviderMapping.rejected(
                key.provider(), key.entityType(), key.providerEntityId(),
                key.season(), key.phase(), now);
    }

    private ProviderMapping revisedMapping(
            ProviderMapping current,
            ValidatedCommand command,
            Instant now) {
        return command.decisionType() == MappingDecisionType.CONFIRM
                ? current.confirm(command.canonicalEntityId(), now)
                : current.reject(now);
    }

    private ProviderMappingDecision toDecision(
            UUID decisionId,
            UUID receiptId,
            ProviderMapping previous,
            ProviderMapping resulting,
            ValidatedCommand command,
            OperatorIdentity operator,
            Instant decidedAt) {
        return new ProviderMappingDecision(
                decisionId,
                resulting.id(),
                receiptId,
                command.decisionType(),
                command.expectedVersion(),
                resulting.version(),
                previous == null ? null : previous.status(),
                previous == null ? null : previous.canonicalEntityId(),
                previous == null ? null : previous.confidence(),
                resulting.status(),
                resulting.canonicalEntityId(),
                resulting.confidence(),
                operator.value(),
                command.sanitizedJustification(),
                decidedAt);
    }

    private boolean canonicalEntityExists(
            ProviderEntityType entityType,
            UUID canonicalEntityId) {
        return switch (entityType) {
            case COMPETITION -> catalogRepository.existsCompetition(canonicalEntityId);
            case TEAM -> catalogRepository.existsTeam(canonicalEntityId);
            case FIXTURE -> catalogRepository.existsFixture(canonicalEntityId);
            case SNAPSHOT -> false;
        };
    }

    private ControlCommandType commandType(MappingDecisionType decisionType) {
        return decisionType == MappingDecisionType.CONFIRM
                ? ControlCommandType.MAPPING_CONFIRM
                : ControlCommandType.MAPPING_REJECT;
    }

    private String commandSha256(
            MappingDecisionCommand command,
            String normalizedRawJustification) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ProviderMappingKey key = command.mappingKey();
            updateDigest(digest, "mapping-decision-v1");
            updateDigest(digest, key.provider());
            updateDigest(digest, key.entityType().name());
            updateDigest(digest, key.providerEntityId());
            updateDigest(digest, key.season());
            updateDigest(digest, key.phase());
            updateDigest(digest, command.decisionType().name());
            updateDigest(digest, command.canonicalEntityId() == null
                    ? ""
                    : command.canonicalEntityId().toString());
            updateDigest(digest, Long.toString(command.expectedVersion()));
            updateDigest(digest, normalizedRawJustification);
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private record ValidatedCommand(
            ProviderMappingKey mappingKey,
            MappingDecisionType decisionType,
            UUID canonicalEntityId,
            long expectedVersion,
            String idempotencyKey,
            String sanitizedJustification,
            String commandSha256) {
    }
}
