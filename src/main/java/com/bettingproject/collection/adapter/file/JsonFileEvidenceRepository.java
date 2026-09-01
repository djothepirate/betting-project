package com.bettingproject.collection.adapter.file;

import com.bettingproject.collection.application.EvidenceCollectionRequest;
import com.bettingproject.collection.application.EvidenceRepository;
import com.bettingproject.collection.application.ProviderCallResult;
import com.bettingproject.collection.application.StoredEvidence;
import com.bettingproject.collection.domain.RawSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import tools.jackson.databind.ObjectMapper;

public final class JsonFileEvidenceRepository implements EvidenceRepository {

    private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH-mm-ss-SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final Path outputRoot;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JsonFileEvidenceRepository(Path outputRoot, ObjectMapper objectMapper, Clock clock) {
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public StoredEvidence store(EvidenceCollectionRequest request, ProviderCallResult response) throws IOException {
        String callId = UUID.randomUUID().toString();
        String runId = RUN_ID_FORMAT.format(response.startedAt()) + "-" + callId.substring(0, 8);
        Path callDirectory = outputRoot
                .resolve(runId)
                .resolve(request.sampleId())
                .resolve(response.provider().toLowerCase().replaceAll("[^a-z0-9]+", "-"))
                .resolve(request.collectionWindow().toLowerCase().replace('_', '-'))
                .normalize();
        assertInsideOutputRoot(callDirectory);
        Files.createDirectories(callDirectory);

        String stem = request.endpoint().fileStem();
        Path rawPath = callDirectory.resolve(stem + ".raw.json");
        Path metadataPath = callDirectory.resolve(stem + ".metadata.json");
        RawSnapshot snapshot = RawSnapshot.capture(
                response.provider(),
                response.endpoint(),
                response.completedAt(),
                response.payload(),
                response.connectorVersion());

        writeAtomically(rawPath, snapshot.payload());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", "enrichment-evidence-v1");
        metadata.put("callId", callId);
        metadata.put("provider", response.provider());
        metadata.put("sampleId", request.sampleId());
        metadata.put("providerMatchId", request.providerMatchId());
        metadata.put("scenarios", request.scenarios());
        metadata.put("collectionWindow", request.collectionWindow());
        metadata.put("endpointFamily", request.endpoint().name());
        metadata.put("endpoint", response.endpoint());
        metadata.put("startedAt", response.startedAt().toString());
        metadata.put("completedAt", response.completedAt().toString());
        metadata.put("latencyMs", response.latencyMs());
        metadata.put("httpStatus", response.httpStatus());
        metadata.put("responseHeaders", safeResponseHeaders(response.responseHeaders()));
        metadata.put("rawFile", rawPath.getFileName().toString());
        metadata.put("sha256", snapshot.sha256());
        metadata.put("bytes", snapshot.payload().length);
        metadata.put("connectorVersion", snapshot.connectorVersion());
        metadata.put("storedAt", Instant.now(clock).toString());
        metadata.put("credentialHandling", "Environment variable only; authentication headers are not persisted.");
        writeAtomically(metadataPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(metadata));

        return new StoredEvidence(
                callId,
                rawPath,
                metadataPath,
                snapshot.sha256(),
                response.httpStatus(),
                response.latencyMs());
    }

    private void assertInsideOutputRoot(Path path) {
        if (!path.startsWith(outputRoot)) {
            throw new IllegalArgumentException("Evidence path escapes the configured output root");
        }
    }

    private static Map<String, String> safeResponseHeaders(Map<String, String> responseHeaders) {
        Map<String, String> safe = new LinkedHashMap<>();
        responseHeaders.forEach((name, value) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.equals("content-type")
                    || normalized.equals("date")
                    || normalized.equals("retry-after")
                    || normalized.startsWith("x-ratelimit-")
                    || normalized.startsWith("x-requests-")
                    || normalized.startsWith("x-requestcounter-")) {
                safe.put(normalized, value);
            }
        });
        return Map.copyOf(safe);
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
}
