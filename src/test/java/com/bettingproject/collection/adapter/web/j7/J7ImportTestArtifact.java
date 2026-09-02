package com.bettingproject.collection.adapter.web.j7;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class J7ImportTestArtifact {

    static final String EXPORT_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    static final String CANONICAL_EVENT_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    static final String SOURCE_SHA = "c".repeat(64);
    static final String NORMALIZED_SHA = "d".repeat(64);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private J7ImportTestArtifact() {
    }

    static Artifact valid() {
        ObjectNode data = data();
        ArrayNode sources = sources();
        String dataSha256 = sha256(writeCompact(data));
        String sourceSetSha256 = sha256(writeCompact(sources));

        ObjectNode manifest = MAPPER.createObjectNode();
        manifest.put("exportId", EXPORT_ID);
        manifest.put("schemaId", J7ImportHttpContract.SCHEMA_ID);
        manifest.put("schemaVersion", J7ImportHttpContract.SCHEMA_VERSION);
        manifest.put("generatedAt", "2026-09-02T00:00:00Z");
        manifest.put("generatorVersion", "int001-test-v1");
        manifest.put("selectionMode", "LATEST_AVAILABLE");
        manifest.put("dataSha256", dataSha256);
        manifest.put("sourceSetSha256", sourceSetSha256);
        ObjectNode validation = manifest.putObject("validation");
        validation.put("status", "HUMAN_VALIDATED");
        validation.put("decidedAt", "2026-09-02T00:01:00Z");
        validation.putNull("rejectionReason");
        manifest.set("sources", sources);
        ArrayNode warnings = manifest.putArray("warnings");
        for (String component : List.of(
                "EVENT_DETAILS", "EVENT_STATISTICS", "EVENT_INCIDENTS", "EVENT_LINEUPS")) {
            ObjectNode warning = warnings.addObject();
            warning.put("code", "MISSING_COMPONENT");
            warning.put("component", component);
        }
        ObjectNode syntheticWarning = warnings.addObject();
        syntheticWarning.put("code", "SYNTHETIC_SOURCE");
        syntheticWarning.put("component", "EVENT_STATE");

        ObjectNode root = MAPPER.createObjectNode();
        root.set("manifest", manifest);
        root.set("data", data);
        return fromRoot(root);
    }

    static Artifact fromRoot(ObjectNode root) {
        byte[] compact = writeCompact(root);
        byte[] body = Arrays.copyOf(compact, compact.length + 1);
        body[body.length - 1] = (byte) '\n';
        return fromBody(body, root);
    }

    static Artifact fromBody(byte[] body, ObjectNode identitySource) {
        ObjectNode manifest = (ObjectNode) identitySource.get("manifest");
        String exportId = manifest == null || !manifest.has("exportId")
                ? EXPORT_ID
                : manifest.get("exportId").stringValue();
        JsonNode dataShaNode = manifest == null ? null : manifest.get("dataSha256");
        String dataSha = dataShaNode != null && dataShaNode.isString()
                ? dataShaNode.stringValue()
                : "a".repeat(64);
        String fileSha = sha256(body);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put(J7ImportHttpContract.CONTENT_TYPE,
                List.of(J7ImportHttpContract.REQUEST_MEDIA_TYPE));
        headers.put(J7ImportHttpContract.ACCEPT,
                List.of(J7ImportHttpContract.ACK_MEDIA_TYPE));
        headers.put(J7ImportHttpContract.CONTENT_LENGTH,
                List.of(Integer.toString(body.length)));
        headers.put(J7ImportHttpContract.PROTOCOL_VERSION_HEADER,
                List.of(J7ImportHttpContract.PROTOCOL_VERSION));
        headers.put(J7ImportHttpContract.EXPORT_ID, List.of(exportId));
        headers.put(J7ImportHttpContract.FILE_SHA256, List.of(fileSha));
        headers.put(J7ImportHttpContract.DATA_SHA256, List.of(dataSha));
        headers.put(J7ImportHttpContract.IDEMPOTENCY_KEY,
                List.of("j7:" + exportId + ":sha256:" + fileSha));
        return new Artifact(headers, body, identitySource.deepCopy());
    }

    static ObjectNode parse(byte[] body) {
        try {
            return (ObjectNode) MAPPER.readTree(body);
        }
        catch (JacksonException exception) {
            throw new AssertionError(exception);
        }
    }

    static byte[] writeCompact(JsonNode node) {
        try {
            return MAPPER.writeValueAsBytes(node);
        }
        catch (JacksonException exception) {
            throw new AssertionError(exception);
        }
    }

    static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    static byte[] bytes(String value) {
        return value.getBytes(UTF_8);
    }

    private static ObjectNode data() {
        ObjectNode data = MAPPER.createObjectNode();
        ObjectNode identity = data.putObject("identity");
        identity.put("canonicalEventId", CANONICAL_EVENT_ID);
        identity.put("provider", "SOFASCORE");
        identity.put("providerEventId", 42L);

        ObjectNode eventState = data.putObject("eventState");
        eventState.put("availability", "PRESENT");
        eventState.put("startsAt", "2026-09-03T18:00:00Z");
        team(eventState.putObject("homeTeam"), 101L, "Home FC");
        team(eventState.putObject("awayTeam"), 202L, "Away FC");
        ObjectNode status = eventState.putObject("status");
        status.put("type", "SCHEDULED");
        status.putNull("description");
        eventState.putNull("tournament");

        ObjectNode details = data.putObject("eventDetails");
        details.put("availability", "MISSING");
        details.putNull("details");
        missingFamily(data.putObject("statistics"), "metrics");
        missingFamily(data.putObject("incidents"), "incidents");
        missingFamily(data.putObject("lineups"), "lineups");
        return data;
    }

    private static ArrayNode sources() {
        ArrayNode sources = MAPPER.createArrayNode();
        ObjectNode state = sources.addObject();
        state.put("component", "EVENT_STATE");
        state.put("availability", "PRESENT");
        state.put("observationId", 1L);
        state.put("sourceKind", "SYNTHETIC_FIXTURE");
        state.put("sourceReference", "int001-fixture");
        state.putNull("snapshotId");
        state.put("fixtureId", "int001-fixture");
        state.put("sourceSha256", SOURCE_SHA);
        state.put("normalizedSha256", NORMALIZED_SHA);
        state.put("parserVersion", "1.0.0");
        state.put("receivedAt", "2026-09-02T00:00:00Z");
        state.put("rawPayloadState", "NOT_APPLICABLE");
        state.putNull("completeness");
        for (String component : List.of(
                "EVENT_DETAILS", "EVENT_STATISTICS", "EVENT_INCIDENTS", "EVENT_LINEUPS")) {
            ObjectNode missing = sources.addObject();
            missing.put("component", component);
            missing.put("availability", "MISSING");
            missing.putNull("observationId");
            missing.putNull("sourceKind");
            missing.putNull("sourceReference");
            missing.putNull("snapshotId");
            missing.putNull("fixtureId");
            missing.putNull("sourceSha256");
            missing.putNull("normalizedSha256");
            missing.putNull("parserVersion");
            missing.putNull("receivedAt");
            missing.putNull("rawPayloadState");
            missing.putNull("completeness");
        }
        return sources;
    }

    private static void team(ObjectNode target, long providerTeamId, String name) {
        target.put("providerTeamId", providerTeamId);
        target.put("name", name);
    }

    private static void missingFamily(ObjectNode target, String payloadField) {
        target.put("availability", "MISSING");
        target.putNull("completeness");
        target.putNull(payloadField);
    }

    record Artifact(Map<String, List<String>> headers, byte[] body, ObjectNode root) {

        Artifact {
            Map<String, List<String>> copiedHeaders = new LinkedHashMap<>();
            headers.forEach((name, values) -> copiedHeaders.put(name, List.copyOf(values)));
            headers = copiedHeaders;
            body = body.clone();
            root = root.deepCopy();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

        @Override
        public ObjectNode root() {
            return root.deepCopy();
        }

        Map<String, List<String>> mutableHeaders() {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            headers.forEach((name, values) -> copy.put(name, List.copyOf(values)));
            return copy;
        }
    }
}
