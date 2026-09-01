package com.bettingproject.collection.adapter.replay;

import com.bettingproject.collection.application.StoredEvidence;
import com.bettingproject.collection.domain.SnapshotHasher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class EvidenceReplayVerifier {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EvidenceReplayVerifier(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ReplayVerification verify(StoredEvidence evidence) throws IOException {
        byte[] payload = Files.readAllBytes(evidence.rawPayload());
        String actualHash = SnapshotHasher.sha256(payload);
        boolean hashMatches = evidence.sha256().equals(actualHash);
        boolean jsonValid = isJson(payload);
        String status = hashMatches && jsonValid ? "PASS" : "FAIL";
        Path replayPath = siblingReplayPath(evidence.metadata());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", "enrichment-replay-v1");
        report.put("verifierVersion", "enrichment-replay-verifier-v1");
        report.put("parserVersion", "json-structure-v1");
        report.put("callId", evidence.callId());
        report.put("verifiedAt", Instant.now(clock).toString());
        report.put("networkCalls", 0);
        report.put("rawFile", evidence.rawPayload().getFileName().toString());
        report.put("expectedSha256", evidence.sha256());
        report.put("actualSha256", actualHash);
        report.put("hashMatches", hashMatches);
        report.put("jsonValid", jsonValid);
        report.put("status", status);
        writeAtomically(replayPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));

        return new ReplayVerification(replayPath, status, hashMatches, jsonValid);
    }

    private boolean isJson(byte[] payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node != null && (node.isObject() || node.isArray());
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    private static Path siblingReplayPath(Path metadataPath) {
        String fileName = metadataPath.getFileName().toString();
        String replayName = fileName.replace(".metadata.json", ".replay.json");
        return metadataPath.resolveSibling(replayName);
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    public record ReplayVerification(Path report, String status, boolean hashMatches, boolean jsonValid) {
    }
}
