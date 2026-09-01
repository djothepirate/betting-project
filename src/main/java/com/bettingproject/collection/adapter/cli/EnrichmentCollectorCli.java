package com.bettingproject.collection.adapter.cli;

import com.bettingproject.collection.adapter.file.JsonFileEvidenceRepository;
import com.bettingproject.collection.adapter.file.DailyProviderCallGuard;
import com.bettingproject.collection.adapter.http.HighlightlyMatchClient;
import com.bettingproject.collection.adapter.replay.EvidenceReplayVerifier;
import com.bettingproject.collection.adapter.replay.EvidenceReplayVerifier.ReplayVerification;
import com.bettingproject.collection.application.CollectionEndpoint;
import com.bettingproject.collection.application.EvidenceCollectionRequest;
import com.bettingproject.collection.application.EvidenceCollectionService;
import com.bettingproject.collection.application.StoredEvidence;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class EnrichmentCollectorCli {

    private static final String API_KEY_ENV = "HIGHLIGHTLY_API_KEY";
    private static final String UNCERTAIN_CALL_MARKER = ".enr-001-call-outcome-uncertain";
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "sample-id", "match-id", "endpoint", "window", "scenarios", "output-root", "manifest");

    private EnrichmentCollectorCli() {
    }

    public static void main(String[] args) {
        int exitCode;
        try {
            exitCode = run(args, System.getenv(), System.out, System.err);
        }
        catch (Exception exception) {
            System.err.println("ENR-001 collection failed: " + safeMessage(exception));
            exitCode = 1;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, Map<String, String> environment, PrintStream out, PrintStream err)
            throws Exception {
        Map<String, String> options = parseArguments(args);
        String apiKey = environment.get(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            err.println("Missing required environment variable: " + API_KEY_ENV);
            return 2;
        }

        EvidenceCollectionRequest request = new EvidenceCollectionRequest(
                required(options, "sample-id"),
                required(options, "match-id"),
                CollectionEndpoint.fromCliValue(required(options, "endpoint")),
                required(options, "window"),
                parseScenarios(required(options, "scenarios")));
        Path outputRoot = Path.of(required(options, "output-root"));
        Path manifestPath = Path.of(required(options, "manifest"));
        ObjectMapper objectMapper = JsonMapper.builder().build();
        new EnrichmentSampleGuard(objectMapper).validate(manifestPath, request);
        Path normalizedOutputRoot = outputRoot.toAbsolutePath().normalize();
        Path repositoryRoot = repositoryRoot(manifestPath);
        if (normalizedOutputRoot.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("Evidence output root must remain outside the Git repository");
        }
        Files.createDirectories(normalizedOutputRoot);
        Path lockPath = normalizedOutputRoot.resolve(".enr-001-collector.lock");
        try (FileChannel lockChannel = FileChannel.open(
                        lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = acquireExclusiveLock(lockChannel)) {
            Path uncertainCallMarker = normalizedOutputRoot.resolve(UNCERTAIN_CALL_MARKER);
            if (Files.exists(uncertainCallMarker)) {
                throw new IllegalStateException(
                        "A previous provider call has an uncertain outcome and requires diagnosis before resuming");
            }
            Clock clock = Clock.systemUTC();
            var client = HighlightlyMatchClient.production(apiKey);
            var budget = new DailyProviderCallGuard(
                    normalizedOutputRoot,
                    objectMapper,
                    clock,
                    ZoneId.of("Europe/Paris"),
                    80,
                    20).assertAllowed(client.provider());
            var repository = new JsonFileEvidenceRepository(normalizedOutputRoot, objectMapper, clock);
            var service = new EvidenceCollectionService(client, repository);
            StoredEvidence evidence;
            ReplayVerification replay;
            Files.createFile(uncertainCallMarker);
            boolean providerCallAccounted = false;
            try {
                evidence = service.collect(request);
                providerCallAccounted = true;
                replay = new EvidenceReplayVerifier(objectMapper, clock).verify(evidence);
            }
            finally {
                if (providerCallAccounted) {
                    Files.deleteIfExists(uncertainCallMarker);
                }
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("provider", client.provider());
            summary.put("sampleId", request.sampleId());
            summary.put("endpointFamily", request.endpoint().name());
            summary.put("collectionWindow", request.collectionWindow());
            summary.put("httpStatus", evidence.httpStatus());
            summary.put("latencyMs", evidence.latencyMs());
            summary.put("sha256", evidence.sha256());
            summary.put("rawFile", logicalPath(normalizedOutputRoot, evidence.rawPayload()));
            summary.put("metadataFile", logicalPath(normalizedOutputRoot, evidence.metadata()));
            summary.put("replayFile", logicalPath(normalizedOutputRoot, replay.report()));
            summary.put("replayStatus", replay.status());
            summary.put("recordedCallsBeforeRequest", budget.recordedCalls());
            summary.put("dailyOperationalMaximum", budget.dailyOperationalMaximum());
            summary.put("providerQuotaReserve", budget.providerQuotaReserve());
            out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));

            if (!"PASS".equals(replay.status())) {
                return 30;
            }
            if (evidence.httpStatus() == 401 || evidence.httpStatus() == 403) {
                err.println("Provider authentication/authorization failure; further calls must stop.");
                return 21;
            }
            if (evidence.httpStatus() == 429) {
                err.println("Provider quota response; no immediate retry is allowed.");
                return 29;
            }
            if (evidence.httpStatus() < 200 || evidence.httpStatus() >= 300) {
                err.println("Provider returned a non-success HTTP status.");
                return 20;
            }
            return 0;
        }
    }

    private static Map<String, String> parseArguments(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Arguments must be supplied as --name value pairs");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            String rawName = args[index];
            if (!rawName.startsWith("--")) {
                throw new IllegalArgumentException("Argument names must start with --");
            }
            String name = rawName.substring(2);
            if (!ALLOWED_ARGUMENTS.contains(name) || result.put(name, args[index + 1]) != null) {
                throw new IllegalArgumentException("Unknown or duplicate argument: --" + name);
            }
        }
        return result;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: --" + name);
        }
        return value;
    }

    private static List<String> parseScenarios(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(scenario -> !scenario.isEmpty())
                .toList();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String secret = System.getenv(API_KEY_ENV);
        return secret == null || secret.isBlank() ? message : message.replace(secret, "[REDACTED]");
    }

    private static String logicalPath(Path outputRoot, Path evidencePath) {
        Path normalizedRoot = outputRoot.toAbsolutePath().normalize();
        Path normalizedEvidence = evidencePath.toAbsolutePath().normalize();
        if (!normalizedEvidence.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Evidence path escapes the configured output root");
        }
        return normalizedRoot.relativize(normalizedEvidence).toString().replace('\\', '/');
    }

    private static Path repositoryRoot(Path manifestPath) {
        Path manifest = manifestPath.toAbsolutePath().normalize();
        Path benchmarkDirectory = manifest.getParent();
        Path docsDirectory = benchmarkDirectory == null ? null : benchmarkDirectory.getParent();
        Path root = docsDirectory == null ? null : docsDirectory.getParent();
        if (benchmarkDirectory == null
                || docsDirectory == null
                || root == null
                || !"benchmark".equals(benchmarkDirectory.getFileName().toString())
                || !"docs".equals(docsDirectory.getFileName().toString())
                || !Files.isRegularFile(root.resolve("pom.xml"))) {
            throw new IllegalArgumentException(
                    "Manifest must be the versioned ENR-001 manifest under docs/benchmark");
        }
        return root;
    }

    private static FileLock acquireExclusiveLock(FileChannel channel) throws java.io.IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new IllegalStateException("Another ENR-001 collector process is already running");
            }
            return lock;
        }
        catch (java.nio.channels.OverlappingFileLockException exception) {
            throw new IllegalStateException("Another ENR-001 collector process is already running", exception);
        }
    }
}
