package com.bettingproject.collection.adapter.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class DailyProviderCallGuard {

    private static final DateTimeFormatter RUN_ID_TIME_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH-mm-ss-SSS'Z'");
    private static final DateTimeFormatter LEGACY_LOCAL_TIME_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/uuuu HH:mm:ss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final ZoneId LEGACY_LOCAL_TIME_ZONE = ZoneId.of("Europe/Paris");

    private final Path outputRoot;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ZoneId budgetZone;
    private final int dailyOperationalMaximum;
    private final int providerQuotaReserve;

    public DailyProviderCallGuard(
            Path outputRoot,
            ObjectMapper objectMapper,
            Clock clock,
            ZoneId budgetZone,
            int dailyOperationalMaximum,
            int providerQuotaReserve) {
        if (dailyOperationalMaximum < 1 || providerQuotaReserve < 0) {
            throw new IllegalArgumentException("Daily budget configuration is invalid");
        }
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.budgetZone = budgetZone;
        this.dailyOperationalMaximum = dailyOperationalMaximum;
        this.providerQuotaReserve = providerQuotaReserve;
    }

    public BudgetStatus assertAllowed(String provider) throws IOException {
        List<RecordedCall> allCalls = loadCalls(provider);
        LocalDate budgetDate = LocalDate.now(clock.withZone(budgetZone));
        List<RecordedCall> budgetDayCalls = allCalls.stream()
                .filter(call -> call.startedAt().atZone(budgetZone).toLocalDate().equals(budgetDate))
                .toList();
        if (budgetDayCalls.size() >= dailyOperationalMaximum) {
            throw new IllegalStateException("Daily operational call budget is exhausted");
        }
        if (allCalls.stream().anyMatch(call -> call.httpStatus() == 401 || call.httpStatus() == 403)) {
            throw new IllegalStateException("Provider is suspended after an authentication or authorization failure");
        }
        if (allCalls.stream().anyMatch(call -> call.httpStatus() == 429)) {
            throw new IllegalStateException("Provider is suspended after a quota response until diagnosis");
        }

        RecordedCall latestQuotaObservation = allCalls.stream()
                .filter(call -> call.providerRemaining() != null)
                .max(Comparator.comparing(RecordedCall::startedAt))
                .orElse(null);
        Integer effectiveProviderRemaining = null;
        if (latestQuotaObservation != null) {
            long laterCallsWithoutAQuotaObservation = allCalls.stream()
                    .filter(call -> call.startedAt().isAfter(latestQuotaObservation.startedAt()))
                    .count();
            effectiveProviderRemaining = Math.max(
                    0,
                    latestQuotaObservation.providerRemaining()
                            - Math.toIntExact(laterCallsWithoutAQuotaObservation));
        }
        if (effectiveProviderRemaining != null && effectiveProviderRemaining <= providerQuotaReserve) {
            throw new IllegalStateException("Provider quota reserve has been reached");
        }
        return new BudgetStatus(
                budgetDayCalls.size(), dailyOperationalMaximum, providerQuotaReserve, effectiveProviderRemaining);
    }

    private List<RecordedCall> loadCalls(String provider) throws IOException {
        if (!Files.exists(outputRoot)) {
            return List.of();
        }
        List<RecordedCall> calls = new ArrayList<>();
        try (var paths = Files.walk(outputRoot)) {
            for (Path metadataPath : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".metadata.json"))
                    .toList()) {
                JsonNode metadata = objectMapper.readTree(metadataPath.toFile());
                if (!provider.equals(metadata.path("provider").asText())) {
                    continue;
                }
                Instant startedAt = startedAt(metadata, metadataPath);
                calls.add(new RecordedCall(
                        startedAt,
                        metadata.path("httpStatus").asInt(),
                        providerRemaining(metadata.path("responseHeaders"), metadataPath)));
            }
        }
        return List.copyOf(calls);
    }

    private static Instant startedAt(JsonNode metadata, Path metadataPath) throws IOException {
        Instant runStartedAt = runStartedAt(metadataPath);
        String value = metadata.path("startedAt").asText();
        try {
            Instant metadataStartedAt = Instant.parse(value);
            if (!metadataStartedAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS).equals(runStartedAt)) {
                throw new IOException("Evidence startedAt does not match its run id: " + metadataPath);
            }
            return metadataStartedAt;
        }
        catch (DateTimeParseException ignored) {
            Instant legacyStartedAt;
            try {
                legacyStartedAt = LocalDateTime.parse(value, LEGACY_LOCAL_TIME_FORMAT)
                        .atZone(LEGACY_LOCAL_TIME_ZONE)
                        .toInstant();
            }
            catch (DateTimeParseException legacyException) {
                throw new IOException("Invalid evidence startedAt: " + metadataPath, legacyException);
            }
            if (!legacyStartedAt.equals(runStartedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))) {
                throw new IOException("Legacy evidence startedAt does not match its run id: " + metadataPath);
            }
            return runStartedAt;
        }
    }

    private static Instant runStartedAt(Path metadataPath) throws IOException {
        Path runDirectory = metadataPath.getParent();
        for (int level = 0; level < 3 && runDirectory != null; level++) {
            runDirectory = runDirectory.getParent();
        }
        String runId = runDirectory == null ? "" : runDirectory.getFileName().toString();
        if (!runId.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}-\\d{3}Z-[0-9a-fA-F]{8}$")) {
            throw new IOException("Invalid evidence run id: " + metadataPath);
        }
        String timestamp = runId.substring(0, 24);
        try {
            return LocalDateTime.parse(timestamp, RUN_ID_TIME_FORMAT).toInstant(ZoneOffset.UTC);
        }
        catch (DateTimeParseException exception) {
            throw new IOException("Invalid evidence run id timestamp: " + metadataPath, exception);
        }
    }

    private static Integer providerRemaining(JsonNode headers, Path metadataPath) throws IOException {
        Integer conservativeRemaining = null;
        var fields = headers.properties().iterator();
        while (fields.hasNext()) {
            var field = fields.next();
            String name = field.getKey().toLowerCase(Locale.ROOT);
            if ((name.startsWith("x-ratelimit-")
                    || name.startsWith("x-requests-")
                    || name.startsWith("x-requestcounter-"))
                    && name.contains("remaining")) {
                int parsed;
                try {
                    parsed = Integer.parseInt(field.getValue().asText());
                }
                catch (NumberFormatException exception) {
                    throw new IOException("Invalid provider quota header in evidence: " + metadataPath, exception);
                }
                if (parsed < 0) {
                    throw new IOException("Negative provider quota header in evidence: " + metadataPath);
                }
                conservativeRemaining = conservativeRemaining == null
                        ? parsed
                        : Math.min(conservativeRemaining, parsed);
            }
        }
        return conservativeRemaining;
    }

    private record RecordedCall(Instant startedAt, int httpStatus, Integer providerRemaining) {
    }

    public record BudgetStatus(
            int recordedCalls,
            int dailyOperationalMaximum,
            int providerQuotaReserve,
            Integer effectiveProviderRemaining) {
    }
}
