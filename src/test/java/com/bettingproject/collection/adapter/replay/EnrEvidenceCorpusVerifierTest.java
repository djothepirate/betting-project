package com.bettingproject.collection.adapter.replay;

import com.bettingproject.collection.domain.SnapshotHasher;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class EnrEvidenceCorpusVerifierTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @TempDir
    Path temporaryDirectory;

    @Test
    void verifiesAStandardReplayWithoutChangingTheExternalCorpus() throws Exception {
        CorpusFixture fixture = createCorpus(false);
        byte[] replayBeforeVerification = Files.readAllBytes(fixture.replayPath());
        var verifier = new EnrEvidenceCorpusVerifier(objectMapper, 1);

        var first = verifier.verify(fixture.evidenceRoot(), fixture.indexPath());
        var second = verifier.verify(fixture.evidenceRoot(), fixture.indexPath());

        assertThat(first).isEqualTo(second);
        assertThat(first.passed()).isTrue();
        assertThat(first.expectedEntries()).isEqualTo(1);
        assertThat(first.indexedEntries()).isEqualTo(1);
        assertThat(first.verifiedEntries()).isEqualTo(1);
        assertThat(first.failedEntries()).isZero();
        assertThat(first.failures()).isEmpty();
        assertThat(Files.readAllBytes(fixture.replayPath())).isEqualTo(replayBeforeVerification);
    }

    @Test
    void acceptsTheExplicitFootballDataLegacyReplayWithoutNetworkCallsField() throws Exception {
        CorpusFixture fixture = createCorpus(true);

        var summary = new EnrEvidenceCorpusVerifier(objectMapper, 1)
                .verify(fixture.evidenceRoot(), fixture.indexPath());

        assertThat(summary.passed()).isTrue();
        assertThat(summary.failures()).isEmpty();
    }

    @Test
    void acceptsAndReportsALegacyLocalMetadataTimestamp() throws Exception {
        CorpusFixture fixture = createCorpus(false);
        ObjectNode metadata = (ObjectNode) objectMapper.readTree(fixture.metadataPath().toFile());
        metadata.put("startedAt", "14/08/2026 20:19:54");
        writeJson(fixture.metadataPath(), metadata);

        var summary = new EnrEvidenceCorpusVerifier(objectMapper, 1)
                .verify(fixture.evidenceRoot(), fixture.indexPath());

        assertThat(summary.passed()).isTrue();
        assertThat(summary.warningCount()).isEqualTo(1);
        assertThat(summary.warnings()).extracting(EnrEvidenceCorpusVerifier.VerificationWarning::code)
                .containsExactly("LEGACY_LOCAL_TIMESTAMP");
    }

    @Test
    void rejectsAnArbitraryMetadataTimestampInsteadOfTreatingItAsLegacy() throws Exception {
        CorpusFixture fixture = createCorpus(false);
        ObjectNode metadata = (ObjectNode) objectMapper.readTree(fixture.metadataPath().toFile());
        metadata.put("startedAt", "not-a-timestamp");
        writeJson(fixture.metadataPath(), metadata);

        var summary = new EnrEvidenceCorpusVerifier(objectMapper, 1)
                .verify(fixture.evidenceRoot(), fixture.indexPath());

        assertThat(summary.passed()).isFalse();
        assertThat(summary.warningCount()).isZero();
        assertThat(summary.failures()).extracting(EnrEvidenceCorpusVerifier.VerificationFailure::code)
                .contains("METADATA_FIELD_MISMATCH");
    }

    @Test
    void rejectsALegacyMetadataTimestampThatDoesNotMatchTheRunId() throws Exception {
        CorpusFixture fixture = createCorpus(false);
        ObjectNode metadata = (ObjectNode) objectMapper.readTree(fixture.metadataPath().toFile());
        metadata.put("startedAt", "14/08/2026 20:20:54");
        writeJson(fixture.metadataPath(), metadata);

        var summary = new EnrEvidenceCorpusVerifier(objectMapper, 1)
                .verify(fixture.evidenceRoot(), fixture.indexPath());

        assertThat(summary.passed()).isFalse();
        assertThat(summary.warningCount()).isZero();
        assertThat(summary.failures()).extracting(EnrEvidenceCorpusVerifier.VerificationFailure::code)
                .contains("METADATA_FIELD_MISMATCH");
    }

    @Test
    void derivesBoxScoreLogicalIdsWithHyphens() throws Exception {
        CorpusFixture fixture = createCorpus(false);
        ObjectNode metadata = (ObjectNode) objectMapper.readTree(fixture.metadataPath().toFile());
        metadata.put("endpointFamily", "BOX_SCORE");
        writeJson(fixture.metadataPath(), metadata);
        ObjectNode index = (ObjectNode) objectMapper.readTree(fixture.indexPath().toFile());
        ObjectNode entry = (ObjectNode) index.get("entries").get(0);
        entry.put("endpointFamily", "BOX_SCORE");
        entry.put("logicalId", "ENR-P01-BOX-SCORE-001");
        writeJson(fixture.indexPath(), index);

        var summary = new EnrEvidenceCorpusVerifier(objectMapper, 1)
                .verify(fixture.evidenceRoot(), fixture.indexPath());

        assertThat(summary.passed()).isTrue();
    }

    @Test
    void reportsAHashMismatchAndTheCliReturnsANonZeroCode() throws Exception {
        CorpusFixture fixture = createCorpus(false);
        Files.writeString(fixture.rawPath(), "{\"tampered\":true}", StandardCharsets.UTF_8);
        var verifier = new EnrEvidenceCorpusVerifier(objectMapper, 1);

        var summary = verifier.verify(fixture.evidenceRoot(), fixture.indexPath());
        var standardOutput = new ByteArrayOutputStream();
        var standardError = new ByteArrayOutputStream();
        int exitCode = EnrEvidenceCorpusVerifierCli.run(
                new String[] {
                    "--evidence-root", fixture.evidenceRoot().toString(),
                    "--index", fixture.indexPath().toString()
                },
                new PrintStream(standardOutput, true, StandardCharsets.UTF_8),
                new PrintStream(standardError, true, StandardCharsets.UTF_8),
                objectMapper,
                verifier);

        assertThat(summary.passed()).isFalse();
        assertThat(summary.failures()).extracting(EnrEvidenceCorpusVerifier.VerificationFailure::code)
                .contains("HASH_MISMATCH");
        assertThat(exitCode).isEqualTo(1);
        assertThat(standardOutput.toString(StandardCharsets.UTF_8)).contains("\"status\" : \"FAIL\"");
        assertThat(standardError.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void rejectsAPathTraversalBeforeReadingTheOutsideFile() throws Exception {
        CorpusFixture fixture = createCorpus(false);
        Files.writeString(temporaryDirectory.resolve("outside.json"), "{\"outside\":true}", StandardCharsets.UTF_8);
        ObjectNode index = (ObjectNode) objectMapper.readTree(fixture.indexPath().toFile());
        ((ObjectNode) index.get("entries").get(0)).put("rawPath", "../outside.json");
        writeJson(fixture.indexPath(), index);

        var summary = new EnrEvidenceCorpusVerifier(objectMapper, 1)
                .verify(fixture.evidenceRoot(), fixture.indexPath());

        assertThat(summary.passed()).isFalse();
        assertThat(summary.failures()).extracting(EnrEvidenceCorpusVerifier.VerificationFailure::code)
                .contains("PATH_OUTSIDE_ROOT");
    }

    @Test
    void rejectsAFailedReplay() throws Exception {
        CorpusFixture fixture = createCorpus(false);
        ObjectNode replay = (ObjectNode) objectMapper.readTree(fixture.replayPath().toFile());
        replay.put("status", "FAIL");
        writeJson(fixture.replayPath(), replay);

        var summary = new EnrEvidenceCorpusVerifier(objectMapper, 1)
                .verify(fixture.evidenceRoot(), fixture.indexPath());

        assertThat(summary.passed()).isFalse();
        assertThat(summary.failures()).extracting(EnrEvidenceCorpusVerifier.VerificationFailure::code)
                .contains("REPLAY_STATUS_MISMATCH");
    }

    private CorpusFixture createCorpus(boolean legacyFootballDataReplay) throws Exception {
        Path evidenceRoot = Files.createDirectories(temporaryDirectory.resolve("evidence"));
        String runId = "2026-08-14T18-19-54-575Z-92d42b9a";
        String provider = legacyFootballDataReplay ? "football-data.org" : "Highlightly";
        String providerDirectory = legacyFootballDataReplay ? "football-data-org" : "highlightly";
        String sampleId = legacyFootballDataReplay ? "ENR-P08" : "ENR-P01";
        String callId = legacyFootballDataReplay
                ? "cdcd259d-11d5-47c5-ad56-ea501ea7a709"
                : "e97ce5b8-7d96-4c31-859a-f7655c8a43c1";
        String startedAt = "2026-08-14T18:19:54.575Z";
        String connectorVersion = legacyFootballDataReplay
                ? "football-data-detail-v1"
                : "highlightly-enrichment-v1";
        String parserVersion = legacyFootballDataReplay
                ? "football-data-detail-parser-v1"
                : "highlightly-enrichment-parser-v1";
        String replaySchema = legacyFootballDataReplay
                ? EnrEvidenceCorpusVerifier.FOOTBALL_DATA_LEGACY_REPLAY_SCHEMA
                : EnrEvidenceCorpusVerifier.STANDARD_REPLAY_SCHEMA;
        String logicalId = legacyFootballDataReplay
                ? sampleId + "-FD-DETAIL-001"
                : sampleId + "-DETAIL-001";
        Path callDirectory = Files.createDirectories(
                evidenceRoot.resolve(runId).resolve(sampleId).resolve(providerDirectory).resolve("t-60"));
        Path rawPath = callDirectory.resolve("detail.raw.json");
        Path metadataPath = callDirectory.resolve("detail.metadata.json");
        Path replayPath = callDirectory.resolve("detail.replay.json");
        byte[] rawPayload = "{\"data\":{\"id\":567268}}".getBytes(StandardCharsets.UTF_8);
        String sha256 = SnapshotHasher.sha256(rawPayload);
        Files.write(rawPath, rawPayload);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", "enrichment-evidence-v1");
        metadata.put("callId", callId);
        metadata.put("provider", provider);
        metadata.put("sampleId", sampleId);
        metadata.put("collectionWindow", "T-60");
        metadata.put("endpointFamily", "DETAIL");
        metadata.put("startedAt", startedAt);
        metadata.put("httpStatus", 200);
        metadata.put("rawFile", rawPath.getFileName().toString());
        metadata.put("sha256", sha256);
        metadata.put("bytes", rawPayload.length);
        metadata.put("connectorVersion", connectorVersion);
        writeJson(metadataPath, metadata);

        Map<String, Object> replay = new LinkedHashMap<>();
        if (legacyFootballDataReplay) {
            replay.put("provider", provider);
            replay.put("sampleId", sampleId);
            replay.put("endpointFamily", "DETAIL");
            replay.put("sha256", sha256);
            replay.put("status", "PASS");
        }
        else {
            replay.put("schemaVersion", EnrEvidenceCorpusVerifier.STANDARD_REPLAY_SCHEMA);
            replay.put("callId", callId);
            replay.put("networkCalls", 0);
            replay.put("rawFile", rawPath.getFileName().toString());
            replay.put("expectedSha256", sha256);
            replay.put("actualSha256", sha256);
            replay.put("hashMatches", true);
            replay.put("jsonValid", true);
            replay.put("status", "PASS");
        }
        writeJson(replayPath, replay);

        String relativeDirectory = runId + "/" + sampleId + "/" + providerDirectory + "/t-60/";
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("logicalId", logicalId);
        entry.put("runId", runId);
        entry.put("callId", callId);
        entry.put("provider", provider);
        entry.put("sampleId", sampleId);
        entry.put("endpointFamily", "DETAIL");
        entry.put("collectionWindow", "T-60");
        entry.put("startedAt", startedAt);
        entry.put("httpStatus", 200);
        entry.put("bytes", rawPayload.length);
        entry.put("sha256", sha256);
        entry.put("connectorVersion", connectorVersion);
        entry.put("parserVersion", parserVersion);
        entry.put("parserVersionSource", "FINALIZATION_CLASSIFICATION");
        entry.put("replaySchema", replaySchema);
        entry.put("replayStatus", "PASS");
        entry.put("metadataPath", relativeDirectory + metadataPath.getFileName());
        entry.put("rawPath", relativeDirectory + rawPath.getFileName());
        entry.put("replayPath", relativeDirectory + replayPath.getFileName());

        Map<String, Object> index = new LinkedHashMap<>();
        index.put("schemaVersion", EnrEvidenceCorpusVerifier.INDEX_SCHEMA);
        index.put("corpusId", EnrEvidenceCorpusVerifier.CORPUS_ID);
        index.put("expectedCallCount", 1);
        index.put("generatedFrom", EnrEvidenceCorpusVerifier.GENERATED_FROM);
        index.put("entries", List.of(entry));
        Path indexPath = temporaryDirectory.resolve("index.json");
        writeJson(indexPath, index);
        return new CorpusFixture(evidenceRoot, indexPath, metadataPath, rawPath, replayPath);
    }

    private void writeJson(Path target, Object value) throws Exception {
        Files.write(target, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }

    private record CorpusFixture(
            Path evidenceRoot, Path indexPath, Path metadataPath, Path rawPath, Path replayPath) {
    }
}
