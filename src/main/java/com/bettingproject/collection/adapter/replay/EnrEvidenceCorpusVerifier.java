package com.bettingproject.collection.adapter.replay;

import com.bettingproject.collection.domain.SnapshotHasher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only verifier for the externally stored ENR-001 evidence corpus.
 *
 * <p>The verifier deliberately has no HTTP dependency and never writes to the
 * evidence root. The versioned index is the allow-list of files that may be
 * inspected.</p>
 */
public final class EnrEvidenceCorpusVerifier {

    public static final String INDEX_SCHEMA = "enr-001-evidence-index-v1";
    public static final String CORPUS_ID = "ENR-001";
    public static final String GENERATED_FROM = "validated-recovery-corpus-with-finalization-classifications";
    public static final int EXPECTED_CALL_COUNT = 127;
    public static final String STANDARD_REPLAY_SCHEMA = "enrichment-replay-v1";
    public static final String FOOTBALL_DATA_LEGACY_REPLAY_SCHEMA = "football-data-legacy-replay-v1";

    private static final String EVIDENCE_SCHEMA = "enrichment-evidence-v1";
    private static final DateTimeFormatter RUN_ID_TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH-mm-ss-SSS'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LEGACY_LOCAL_TIMESTAMP = DateTimeFormatter
            .ofPattern("dd/MM/uuuu HH:mm:ss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final ZoneId LEGACY_TIMESTAMP_ZONE = ZoneId.of("Europe/Paris");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "corpusId", "expectedCallCount", "generatedFrom", "entries");
    private static final Set<String> ENTRY_FIELDS = Set.of(
            "logicalId",
            "runId",
            "callId",
            "provider",
            "sampleId",
            "endpointFamily",
            "collectionWindow",
            "startedAt",
            "httpStatus",
            "bytes",
            "sha256",
            "connectorVersion",
            "parserVersion",
            "parserVersionSource",
            "replaySchema",
            "replayStatus",
            "metadataPath",
            "rawPath",
            "replayPath");

    private final ObjectMapper objectMapper;
    private final int requiredCallCount;

    public EnrEvidenceCorpusVerifier(ObjectMapper objectMapper) {
        this(objectMapper, EXPECTED_CALL_COUNT);
    }

    EnrEvidenceCorpusVerifier(ObjectMapper objectMapper, int requiredCallCount) {
        if (requiredCallCount < 1) {
            throw new IllegalArgumentException("requiredCallCount must be positive");
        }
        this.objectMapper = objectMapper;
        this.requiredCallCount = requiredCallCount;
    }

    public VerificationSummary verify(Path evidenceRoot, Path indexPath) throws IOException {
        Path normalizedRoot = evidenceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            throw new IOException("Evidence root is not a directory: " + normalizedRoot);
        }
        Path realRoot = normalizedRoot.toRealPath();
        JsonNode index = readJson(indexPath, "Evidence index");
        if (!index.isObject()) {
            throw new IOException("Evidence index root must be a JSON object");
        }

        List<VerificationFailure> failures = new ArrayList<>();
        List<VerificationWarning> warnings = new ArrayList<>();
        rejectUnexpectedFields(index, ROOT_FIELDS, "$index", "$index", failures);
        compareText(index, "schemaVersion", INDEX_SCHEMA, "$index", "$index", failures, "INDEX_SCHEMA_MISMATCH");
        compareText(index, "corpusId", CORPUS_ID, "$index", "$index", failures, "CORPUS_ID_MISMATCH");
        compareText(index, "generatedFrom", GENERATED_FROM, "$index", "$index", failures,
                "INDEX_SOURCE_MISMATCH");

        int declaredCount = requiredInt(index, "expectedCallCount", "$index", "$index", failures);
        if (declaredCount != requiredCallCount) {
            add(failures, "$index", "EXPECTED_CALL_COUNT_MISMATCH", "$index",
                    "expectedCallCount must be " + requiredCallCount + " but was " + declaredCount);
        }

        JsonNode entries = index.get("entries");
        if (entries == null || !entries.isArray()) {
            add(failures, "$index", "REQUIRED_FIELD_INVALID", "$index",
                    "entries must be a JSON array");
            return summary(declaredCount, 0, 0, failures, warnings);
        }
        if (entries.size() != declaredCount) {
            add(failures, "$index", "ENTRY_COUNT_MISMATCH", "$index",
                    "entries contains " + entries.size() + " items but expectedCallCount is " + declaredCount);
        }

        Set<String> logicalIds = new HashSet<>();
        Set<String> callIds = new HashSet<>();
        Set<Path> referencedFiles = new HashSet<>();
        Map<Integer, String> expectedLogicalIds = expectedLogicalIds(entries);
        int verifiedEntries = 0;
        for (int indexPosition = 0; indexPosition < entries.size(); indexPosition++) {
            JsonNode entry = entries.get(indexPosition);
            String fallbackId = String.format(Locale.ROOT, "#%03d", indexPosition + 1);
            String logicalId = optionalText(entry, "logicalId");
            String entryId = logicalId == null ? fallbackId : logicalId;
            int failuresBeforeEntry = failures.size();
            verifyEntry(
                    entry,
                    entryId,
                    expectedLogicalIds.get(indexPosition),
                    normalizedRoot,
                    realRoot,
                    logicalIds,
                    callIds,
                    referencedFiles,
                    failures,
                    warnings);
            if (failures.size() == failuresBeforeEntry) {
                verifiedEntries++;
            }
        }

        return summary(declaredCount, entries.size(), verifiedEntries, failures, warnings);
    }

    private void verifyEntry(
            JsonNode entry,
            String entryId,
            String expectedLogicalId,
            Path normalizedRoot,
            Path realRoot,
            Set<String> logicalIds,
            Set<String> callIds,
            Set<Path> referencedFiles,
            List<VerificationFailure> failures,
            List<VerificationWarning> warnings) {
        if (entry == null || !entry.isObject()) {
            add(failures, entryId, "ENTRY_INVALID", entryId, "entry must be a JSON object");
            return;
        }
        rejectUnexpectedFields(entry, ENTRY_FIELDS, entryId, entryId, failures);

        String logicalId = requiredText(entry, "logicalId", entryId, entryId, failures);
        String runId = requiredText(entry, "runId", entryId, entryId, failures);
        String callId = requiredText(entry, "callId", entryId, entryId, failures);
        String provider = requiredText(entry, "provider", entryId, entryId, failures);
        String sampleId = requiredText(entry, "sampleId", entryId, entryId, failures);
        String endpointFamily = requiredText(entry, "endpointFamily", entryId, entryId, failures);
        String collectionWindow = requiredText(entry, "collectionWindow", entryId, entryId, failures);
        String startedAt = requiredText(entry, "startedAt", entryId, entryId, failures);
        int httpStatus = requiredInt(entry, "httpStatus", entryId, entryId, failures);
        long expectedBytes = requiredLong(entry, "bytes", entryId, entryId, failures);
        String expectedSha256 = requiredText(entry, "sha256", entryId, entryId, failures);
        String connectorVersion = requiredText(entry, "connectorVersion", entryId, entryId, failures);
        String parserVersion = requiredText(entry, "parserVersion", entryId, entryId, failures);
        String parserVersionSource = requiredText(entry, "parserVersionSource", entryId, entryId, failures);
        String replaySchema = requiredText(entry, "replaySchema", entryId, entryId, failures);
        String replayStatus = requiredText(entry, "replayStatus", entryId, entryId, failures);

        if (logicalId != null && !logicalIds.add(logicalId)) {
            add(failures, entryId, "DUPLICATE_LOGICAL_ID", "logicalId", "logicalId is not unique");
        }
        if (logicalId != null && expectedLogicalId != null && !logicalId.equals(expectedLogicalId)) {
            add(failures, entryId, "LOGICAL_ID_MISMATCH", "logicalId",
                    "logicalId must be " + expectedLogicalId + " when calls are ordered by runId");
        }
        if (callId != null && !callIds.add(callId)) {
            add(failures, entryId, "DUPLICATE_CALL_ID", "callId", "callId is not unique");
        }
        Instant runStartedAt = null;
        if (runId != null && !runId.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}-[0-9]{2}-[0-9]{2}-[0-9]{3}Z-[0-9a-f]{8}")) {
            add(failures, entryId, "RUN_ID_INVALID", "runId", "runId does not match the UTC evidence directory format");
        }
        else if (runId != null) {
            runStartedAt = runStartedAt(runId, entryId, failures);
        }
        if (startedAt != null && runStartedAt != null) {
            try {
                Instant indexedStartedAt = Instant.parse(startedAt);
                if (!startedAt.endsWith("Z") || !indexedStartedAt.equals(runStartedAt)) {
                    add(failures, entryId, "STARTED_AT_RUN_ID_MISMATCH", "startedAt",
                            "startedAt must equal the canonical UTC instant derived from runId");
                }
            }
            catch (DateTimeParseException exception) {
                add(failures, entryId, "STARTED_AT_RUN_ID_MISMATCH", "startedAt",
                        "startedAt must equal the canonical UTC instant derived from runId");
            }
        }
        if (httpStatus < 100 || httpStatus > 599) {
            add(failures, entryId, "HTTP_STATUS_INVALID", "httpStatus", "httpStatus is outside 100..599");
        }
        if (expectedBytes < 0) {
            add(failures, entryId, "BYTE_COUNT_INVALID", "bytes", "bytes must not be negative");
        }
        if (expectedSha256 != null && !expectedSha256.matches("[0-9a-f]{64}")) {
            add(failures, entryId, "SHA256_INVALID", "sha256", "sha256 must contain 64 lowercase hexadecimal characters");
        }
        if (parserVersion != null && parserVersion.isBlank()) {
            add(failures, entryId, "PARSER_VERSION_INVALID", "parserVersion", "parserVersion must not be blank");
        }
        if (parserVersionSource != null
                && !Set.of("RECORDED_REPLAY", "RECORDED_METADATA", "FINALIZATION_CLASSIFICATION")
                        .contains(parserVersionSource)) {
            add(failures, entryId, "PARSER_VERSION_SOURCE_INVALID", "parserVersionSource",
                    "parserVersionSource is not supported");
        }
        if (replayStatus != null && !"PASS".equals(replayStatus)) {
            add(failures, entryId, "REPLAY_STATUS_MISMATCH", "replayStatus", "index replayStatus must be PASS");
        }

        ResolvedEvidencePath metadataPath = resolveEvidencePath(
                entry, "metadataPath", entryId, runId, normalizedRoot, realRoot, referencedFiles, failures);
        ResolvedEvidencePath rawPath = resolveEvidencePath(
                entry, "rawPath", entryId, runId, normalizedRoot, realRoot, referencedFiles, failures);
        ResolvedEvidencePath replayPath = resolveEvidencePath(
                entry, "replayPath", entryId, runId, normalizedRoot, realRoot, referencedFiles, failures);

        JsonNode metadata = readEvidenceJson(metadataPath, entryId, "METADATA_JSON_INVALID", failures);
        JsonNode replay = readEvidenceJson(replayPath, entryId, "REPLAY_JSON_INVALID", failures);
        byte[] rawPayload = readEvidenceBytes(rawPath, entryId, failures);

        String actualSha256 = null;
        if (rawPayload != null) {
            actualSha256 = SnapshotHasher.sha256(rawPayload);
            if (expectedSha256 != null && !expectedSha256.equals(actualSha256)) {
                add(failures, entryId, "HASH_MISMATCH", rawPath.displayPath(),
                        "raw payload SHA-256 does not match the index");
            }
            if (expectedBytes != rawPayload.length) {
                add(failures, entryId, "SIZE_MISMATCH", rawPath.displayPath(),
                        "raw payload contains " + rawPayload.length + " bytes but index declares " + expectedBytes);
            }
            try {
                JsonNode rawJson = objectMapper.readTree(rawPayload);
                if (rawJson == null || (!rawJson.isObject() && !rawJson.isArray())) {
                    add(failures, entryId, "RAW_JSON_INVALID", rawPath.displayPath(),
                            "raw payload must be a JSON object or array");
                }
            }
            catch (RuntimeException exception) {
                add(failures, entryId, "RAW_JSON_INVALID", rawPath.displayPath(), "raw payload is not valid JSON");
            }
        }

        if (metadata != null && metadata.isObject()) {
            compareText(metadata, "schemaVersion", EVIDENCE_SCHEMA, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            compareText(metadata, "callId", callId, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            compareText(metadata, "provider", provider, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            compareText(metadata, "sampleId", sampleId, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            compareText(metadata, "endpointFamily", endpointFamily, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            compareText(metadata, "collectionWindow", collectionWindow, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            verifyMetadataStartedAt(
                    metadata, entryId, runStartedAt, metadataPath.displayPath(), failures, warnings);
            compareInt(metadata, "httpStatus", httpStatus, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            compareLong(metadata, "bytes", expectedBytes, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            compareText(metadata, "sha256", expectedSha256, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            compareText(metadata, "connectorVersion", connectorVersion, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            compareOptionalText(metadata, "parserVersion", parserVersion, entryId, metadataPath.displayPath(), failures,
                    "METADATA_FIELD_MISMATCH");
            compareFileName(metadata, "rawFile", rawPath, entryId, metadataPath.displayPath(), failures);
        }
        else if (metadata != null) {
            add(failures, entryId, "METADATA_JSON_INVALID", metadataPath.displayPath(),
                    "metadata must be a JSON object");
        }

        if (replay != null && replay.isObject()) {
            verifyParserVersionProvenance(
                    metadata, replay, entryId, parserVersion, parserVersionSource,
                    metadataPath, replayPath, failures);
            verifyReplay(
                    replay,
                    entryId,
                    provider,
                    sampleId,
                    endpointFamily,
                    callId,
                    parserVersion,
                    replaySchema,
                    replayStatus,
                    expectedSha256,
                    actualSha256,
                    rawPath,
                    replayPath,
                    failures);
        }
        else if (replay != null) {
            add(failures, entryId, "REPLAY_JSON_INVALID", replayPath.displayPath(), "replay must be a JSON object");
        }
    }

    private static void verifyParserVersionProvenance(
            JsonNode metadata,
            JsonNode replay,
            String entryId,
            String parserVersion,
            String parserVersionSource,
            ResolvedEvidencePath metadataPath,
            ResolvedEvidencePath replayPath,
            List<VerificationFailure> failures) {
        if (parserVersion == null || parserVersionSource == null || metadataPath == null || replayPath == null) {
            return;
        }
        if ("RECORDED_REPLAY".equals(parserVersionSource)) {
            compareText(replay, "parserVersion", parserVersion, entryId, replayPath.displayPath(), failures,
                    "PARSER_VERSION_PROVENANCE_MISMATCH");
            return;
        }
        if ("RECORDED_METADATA".equals(parserVersionSource)) {
            if (metadata == null || !metadata.isObject()) {
                add(failures, entryId, "PARSER_VERSION_PROVENANCE_MISMATCH", metadataPath.displayPath(),
                        "parserVersionSource claims metadata but metadata is unavailable");
                return;
            }
            compareText(metadata, "parserVersion", parserVersion, entryId, metadataPath.displayPath(), failures,
                    "PARSER_VERSION_PROVENANCE_MISMATCH");
            return;
        }
        if ("FINALIZATION_CLASSIFICATION".equals(parserVersionSource)
                && ((metadata != null && metadata.has("parserVersion")) || replay.has("parserVersion"))) {
            add(failures, entryId, "PARSER_VERSION_PROVENANCE_MISMATCH", entryId,
                    "finalization classification must not be presented as a recorded parserVersion");
        }
    }

    private void verifyReplay(
            JsonNode replay,
            String entryId,
            String provider,
            String sampleId,
            String endpointFamily,
            String callId,
            String parserVersion,
            String replaySchema,
            String replayStatus,
            String expectedSha256,
            String actualSha256,
            ResolvedEvidencePath rawPath,
            ResolvedEvidencePath replayPath,
            List<VerificationFailure> failures) {
        if (FOOTBALL_DATA_LEGACY_REPLAY_SCHEMA.equals(replaySchema)) {
            if (!"football-data.org".equals(provider)) {
                add(failures, entryId, "REPLAY_SCHEMA_UNSUPPORTED", replayPath.displayPath(),
                        "legacy replay schema is restricted to football-data.org");
                return;
            }
            if (replay.has("schemaVersion")) {
                add(failures, entryId, "REPLAY_SCHEMA_MISMATCH", replayPath.displayPath(),
                        "legacy football-data replay must not claim a schemaVersion");
            }
            compareText(replay, "sha256", expectedSha256, entryId, replayPath.displayPath(), failures,
                    "REPLAY_FIELD_MISMATCH");
            compareText(replay, "provider", provider, entryId, replayPath.displayPath(), failures,
                    "REPLAY_FIELD_MISMATCH");
            compareText(replay, "sampleId", sampleId, entryId, replayPath.displayPath(), failures,
                    "REPLAY_FIELD_MISMATCH");
            compareText(replay, "endpointFamily", endpointFamily, entryId, replayPath.displayPath(), failures,
                    "REPLAY_FIELD_MISMATCH");
            compareText(replay, "status", replayStatus, entryId, replayPath.displayPath(), failures,
                    "REPLAY_STATUS_MISMATCH");
            compareOptionalText(replay, "parserVersion", parserVersion, entryId, replayPath.displayPath(), failures,
                    "REPLAY_FIELD_MISMATCH");
            verifyOptionalNetworkCalls(replay, entryId, replayPath.displayPath(), failures);
            return;
        }

        if (!STANDARD_REPLAY_SCHEMA.equals(replaySchema)) {
            add(failures, entryId, "REPLAY_SCHEMA_UNSUPPORTED", replayPath.displayPath(),
                    "unsupported replaySchema: " + safe(replaySchema));
            return;
        }
        compareText(replay, "schemaVersion", replaySchema, entryId, replayPath.displayPath(), failures,
                "REPLAY_SCHEMA_MISMATCH");
        compareText(replay, "callId", callId, entryId, replayPath.displayPath(), failures,
                "REPLAY_FIELD_MISMATCH");
        compareText(replay, "expectedSha256", expectedSha256, entryId, replayPath.displayPath(), failures,
                "REPLAY_FIELD_MISMATCH");
        compareText(replay, "actualSha256", actualSha256, entryId, replayPath.displayPath(), failures,
                "REPLAY_FIELD_MISMATCH");
        compareBoolean(replay, "hashMatches", true, entryId, replayPath.displayPath(), failures,
                "REPLAY_FIELD_MISMATCH");
        compareBoolean(replay, "jsonValid", true, entryId, replayPath.displayPath(), failures,
                "REPLAY_FIELD_MISMATCH");
        compareText(replay, "status", replayStatus, entryId, replayPath.displayPath(), failures,
                "REPLAY_STATUS_MISMATCH");
        compareOptionalText(replay, "parserVersion", parserVersion, entryId, replayPath.displayPath(), failures,
                "REPLAY_FIELD_MISMATCH");
        compareFileName(replay, "rawFile", rawPath, entryId, replayPath.displayPath(), failures);
        if (!replay.has("networkCalls") || !replay.get("networkCalls").isIntegralNumber()
                || replay.get("networkCalls").asLong() != 0L) {
            add(failures, entryId, "NETWORK_CALLS_DETECTED", replayPath.displayPath(),
                    "standard replay must declare networkCalls=0");
        }
    }

    private static void verifyOptionalNetworkCalls(
            JsonNode replay, String entryId, String displayPath, List<VerificationFailure> failures) {
        if (replay.has("networkCalls")
                && (!replay.get("networkCalls").isIntegralNumber() || replay.get("networkCalls").asLong() != 0L)) {
            add(failures, entryId, "NETWORK_CALLS_DETECTED", displayPath,
                    "networkCalls must be zero when present");
        }
    }

    private JsonNode readEvidenceJson(
            ResolvedEvidencePath path,
            String entryId,
            String failureCode,
            List<VerificationFailure> failures) {
        if (path == null) {
            return null;
        }
        try {
            return objectMapper.readTree(Files.readAllBytes(path.realPath()));
        }
        catch (IOException | RuntimeException exception) {
            add(failures, entryId, failureCode, path.displayPath(), "file is not valid JSON");
            return null;
        }
    }

    private static byte[] readEvidenceBytes(
            ResolvedEvidencePath path, String entryId, List<VerificationFailure> failures) {
        if (path == null) {
            return null;
        }
        try {
            return Files.readAllBytes(path.realPath());
        }
        catch (IOException exception) {
            add(failures, entryId, "FILE_READ_FAILED", path.displayPath(), "file could not be read");
            return null;
        }
    }

    private static ResolvedEvidencePath resolveEvidencePath(
            JsonNode entry,
            String field,
            String entryId,
            String runId,
            Path normalizedRoot,
            Path realRoot,
            Set<Path> referencedFiles,
            List<VerificationFailure> failures) {
        String value = requiredText(entry, field, entryId, field, failures);
        if (value == null) {
            return null;
        }
        try {
            Path relative = Path.of(value);
            if (relative.isAbsolute()) {
                add(failures, entryId, "PATH_OUTSIDE_ROOT", value, "evidence path must be relative");
                return null;
            }
            Path candidate = normalizedRoot.resolve(relative).normalize();
            if (!candidate.startsWith(normalizedRoot)) {
                add(failures, entryId, "PATH_OUTSIDE_ROOT", value, "evidence path escapes the configured root");
                return null;
            }
            if (runId != null && (relative.getNameCount() == 0 || !runId.equals(relative.getName(0).toString()))) {
                add(failures, entryId, "RUN_ID_PATH_MISMATCH", value,
                        "evidence path must start with the entry runId");
                return null;
            }
            if (!Files.exists(candidate)) {
                add(failures, entryId, "FILE_MISSING", value, "referenced evidence file does not exist");
                return null;
            }
            if (!Files.isRegularFile(candidate)) {
                add(failures, entryId, "FILE_NOT_REGULAR", value, "referenced evidence path is not a regular file");
                return null;
            }
            Path realPath = candidate.toRealPath();
            if (!realPath.startsWith(realRoot)) {
                add(failures, entryId, "PATH_OUTSIDE_ROOT", value,
                        "evidence path resolves outside the configured root");
                return null;
            }
            if (!referencedFiles.add(realPath)) {
                add(failures, entryId, "DUPLICATE_EVIDENCE_PATH", value,
                        "evidence file is referenced more than once");
            }
            return new ResolvedEvidencePath(value.replace('\\', '/'), realPath);
        }
        catch (InvalidPathException exception) {
            add(failures, entryId, "PATH_INVALID", value, "evidence path is invalid");
            return null;
        }
        catch (IOException exception) {
            add(failures, entryId, "FILE_RESOLUTION_FAILED", value, "evidence path could not be resolved");
            return null;
        }
    }

    private static void compareFileName(
            JsonNode node,
            String field,
            ResolvedEvidencePath expectedPath,
            String entryId,
            String displayPath,
            List<VerificationFailure> failures) {
        if (expectedPath == null) {
            return;
        }
        compareText(
                node,
                field,
                expectedPath.realPath().getFileName().toString(),
                entryId,
                displayPath,
                failures,
                "EVIDENCE_FILE_NAME_MISMATCH");
    }

    private static void compareText(
            JsonNode node,
            String field,
            String expected,
            String entryId,
            String displayPath,
            List<VerificationFailure> failures,
            String failureCode) {
        if (expected == null) {
            return;
        }
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || !expected.equals(value.asText())) {
            add(failures, entryId, failureCode, displayPath,
                    field + " differs from the versioned index");
        }
    }

    private static void compareOptionalText(
            JsonNode node,
            String field,
            String expected,
            String entryId,
            String displayPath,
            List<VerificationFailure> failures,
            String failureCode) {
        if (expected == null || !node.has(field)) {
            return;
        }
        compareText(node, field, expected, entryId, displayPath, failures, failureCode);
    }

    private static void compareInt(
            JsonNode node,
            String field,
            int expected,
            String entryId,
            String displayPath,
            List<VerificationFailure> failures,
            String failureCode) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || value.asInt() != expected) {
            add(failures, entryId, failureCode, displayPath,
                    field + " differs from the versioned index");
        }
    }

    private static void compareLong(
            JsonNode node,
            String field,
            long expected,
            String entryId,
            String displayPath,
            List<VerificationFailure> failures,
            String failureCode) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || value.asLong() != expected) {
            add(failures, entryId, failureCode, displayPath,
                    field + " differs from the versioned index");
        }
    }

    private static void compareBoolean(
            JsonNode node,
            String field,
            boolean expected,
            String entryId,
            String displayPath,
            List<VerificationFailure> failures,
            String failureCode) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean() || value.asBoolean() != expected) {
            add(failures, entryId, failureCode, displayPath,
                    field + " differs from the versioned index");
        }
    }

    private static String requiredText(
            JsonNode node,
            String field,
            String entryId,
            String displayPath,
            List<VerificationFailure> failures) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            add(failures, entryId, "REQUIRED_FIELD_INVALID", displayPath,
                    field + " must be a non-blank string");
            return null;
        }
        return value.asText();
    }

    private static int requiredInt(
            JsonNode node,
            String field,
            String entryId,
            String displayPath,
            List<VerificationFailure> failures) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            add(failures, entryId, "REQUIRED_FIELD_INVALID", displayPath,
                    field + " must be a 32-bit integer");
            return Integer.MIN_VALUE;
        }
        return value.asInt();
    }

    private static long requiredLong(
            JsonNode node,
            String field,
            String entryId,
            String displayPath,
            List<VerificationFailure> failures) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            add(failures, entryId, "REQUIRED_FIELD_INVALID", displayPath,
                    field + " must be an integer");
            return Long.MIN_VALUE;
        }
        return value.asLong();
    }

    private static String optionalText(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static void rejectUnexpectedFields(
            JsonNode node,
            Set<String> allowedFields,
            String entryId,
            String displayPath,
            List<VerificationFailure> failures) {
        node.propertyStream()
                .map(java.util.Map.Entry::getKey)
                .filter(field -> !allowedFields.contains(field))
                .sorted()
                .forEach(field -> add(failures, entryId, "UNEXPECTED_FIELD", displayPath,
                        "unexpected field: " + field));
    }

    private JsonNode readJson(Path path, String description) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException(description + " is not a regular file: " + path.toAbsolutePath().normalize());
        }
        try {
            return objectMapper.readTree(Files.readAllBytes(path));
        }
        catch (RuntimeException exception) {
            throw new IOException(description + " is not valid JSON", exception);
        }
    }

    private static Instant runStartedAt(
            String runId, String entryId, List<VerificationFailure> failures) {
        try {
            return Instant.from(RUN_ID_TIMESTAMP.parse(runId.substring(0, 24)));
        }
        catch (DateTimeParseException | IndexOutOfBoundsException exception) {
            add(failures, entryId, "RUN_ID_INVALID", "runId",
                    "runId does not contain a valid UTC evidence timestamp");
            return null;
        }
    }

    private static void verifyMetadataStartedAt(
            JsonNode metadata,
            String entryId,
            Instant runStartedAt,
            String displayPath,
            List<VerificationFailure> failures,
            List<VerificationWarning> warnings) {
        JsonNode value = metadata.get("startedAt");
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            add(failures, entryId, "METADATA_FIELD_MISMATCH", displayPath,
                    "startedAt must be a non-blank string");
            return;
        }
        try {
            Instant metadataStartedAt = OffsetDateTime.parse(value.asText()).toInstant();
            if (runStartedAt != null
                    && !metadataStartedAt.truncatedTo(ChronoUnit.MILLIS).equals(runStartedAt)) {
                add(failures, entryId, "METADATA_FIELD_MISMATCH", displayPath,
                        "metadata startedAt does not match runId at millisecond precision");
            }
        }
        catch (DateTimeParseException exception) {
            Instant legacyStartedAt;
            try {
                legacyStartedAt = LocalDateTime.parse(value.asText(), LEGACY_LOCAL_TIMESTAMP)
                        .atZone(LEGACY_TIMESTAMP_ZONE)
                        .toInstant();
            }
            catch (DateTimeParseException legacyException) {
                add(failures, entryId, "METADATA_FIELD_MISMATCH", displayPath,
                        "startedAt is neither an offset timestamp nor the accepted legacy dd/MM/yyyy HH:mm:ss format");
                return;
            }
            if (runStartedAt != null
                    && !legacyStartedAt.equals(runStartedAt.truncatedTo(ChronoUnit.SECONDS))) {
                add(failures, entryId, "METADATA_FIELD_MISMATCH", displayPath,
                        "legacy metadata startedAt does not match runId at second precision in Europe/Paris");
                return;
            }
            warnings.add(new VerificationWarning(
                    safe(entryId),
                    "LEGACY_LOCAL_TIMESTAMP",
                    safe(displayPath),
                    "metadata startedAt uses the accepted legacy local timestamp format"));
        }
    }

    private static Map<Integer, String> expectedLogicalIds(JsonNode entries) {
        List<LogicalIdMaterial> materials = new ArrayList<>();
        for (int position = 0; position < entries.size(); position++) {
            JsonNode entry = entries.get(position);
            String runId = optionalText(entry, "runId");
            String provider = optionalText(entry, "provider");
            String sampleId = optionalText(entry, "sampleId");
            String endpointFamily = optionalText(entry, "endpointFamily");
            if (runId != null && provider != null && sampleId != null && endpointFamily != null) {
                materials.add(new LogicalIdMaterial(position, runId, provider, sampleId, endpointFamily));
            }
        }
        materials.sort(Comparator.comparing(LogicalIdMaterial::runId)
                .thenComparingInt(LogicalIdMaterial::position));
        Map<String, Integer> ordinals = new HashMap<>();
        Map<Integer, String> result = new HashMap<>();
        for (LogicalIdMaterial material : materials) {
            String group = material.provider() + "\u0000" + material.sampleId() + "\u0000" + material.endpointFamily();
            int ordinal = ordinals.merge(group, 1, Integer::sum);
            String providerMarker = "football-data.org".equals(material.provider()) ? "-FD" : "";
            String expected = material.sampleId()
                    + providerMarker
                    + "-"
                    + material.endpointFamily().replace('_', '-')
                    + "-"
                    + String.format(Locale.ROOT, "%03d", ordinal);
            result.put(material.position(), expected);
        }
        return result;
    }

    private VerificationSummary summary(
            int declaredCount,
            int indexedEntries,
            int verifiedEntries,
            List<VerificationFailure> failures,
            List<VerificationWarning> warnings) {
        List<VerificationFailure> orderedFailures = failures.stream()
                .sorted(Comparator.comparing(VerificationFailure::logicalId)
                        .thenComparing(VerificationFailure::code)
                        .thenComparing(VerificationFailure::path)
                        .thenComparing(VerificationFailure::message))
                .toList();
        int failedEntries = indexedEntries - verifiedEntries;
        String status = orderedFailures.isEmpty() ? "PASS" : "FAIL";
        List<VerificationWarning> orderedWarnings = warnings.stream()
                .sorted(Comparator.comparing(VerificationWarning::logicalId)
                        .thenComparing(VerificationWarning::code)
                        .thenComparing(VerificationWarning::path)
                        .thenComparing(VerificationWarning::message))
                .toList();
        return new VerificationSummary(
                INDEX_SCHEMA,
                CORPUS_ID,
                declaredCount,
                indexedEntries,
                verifiedEntries,
                failedEntries,
                status,
                orderedFailures,
                orderedWarnings.size(),
                orderedWarnings);
    }

    private static void add(
            List<VerificationFailure> failures, String logicalId, String code, String path, String message) {
        failures.add(new VerificationFailure(safe(logicalId), code, safe(path), message));
    }

    private static String safe(String value) {
        return value == null ? "<missing>" : value;
    }

    private record ResolvedEvidencePath(String displayPath, Path realPath) {
    }

    private record LogicalIdMaterial(
            int position, String runId, String provider, String sampleId, String endpointFamily) {
    }

    public record VerificationSummary(
            String schemaVersion,
            String corpusId,
            int expectedEntries,
            int indexedEntries,
            int verifiedEntries,
            int failedEntries,
            String status,
            List<VerificationFailure> failures,
            int warningCount,
            List<VerificationWarning> warnings) {

        public boolean passed() {
            return "PASS".equals(status);
        }
    }

    public record VerificationFailure(String logicalId, String code, String path, String message) {
    }

    public record VerificationWarning(String logicalId, String code, String path, String message) {
    }
}
