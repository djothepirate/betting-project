package com.bettingproject.collection.adapter.configuration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;

import com.bettingproject.collection.application.capability.ConfiguredProviderCapabilityRegistry;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.SnapshotHasher;
import com.bettingproject.collection.domain.capability.CapabilityAuthorityRole;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.CapabilityEvidenceReference;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.CapabilityStatus;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Local strict parser: no global Jackson configuration or external resource resolution. */
public final class ProviderCapabilityDocumentParser {
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public ProviderCapabilityRegistry parse(byte[] document) {
        try {
            byte[] bytes = document.clone();
            JsonNode root = object(mapper.readTree(bytes), Set.of("schemaVersion", "registryVersion", "entries"));
            if (!"provider-capability-registry-v1".equals(text(root, "schemaVersion"))) {
                throw new IllegalArgumentException("unsupported registry schema");
            }
            var entries = new ArrayList<ProviderCapability>();
            for (JsonNode entry : array(root, "entries")) {
                object(entry, Set.of("key", "route", "status", "authorityRole", "enabled", "evidence"));
                JsonNode key = object(entry.get("key"), Set.of(
                        "provider", "providerCompetitionId", "sourceSeason", "sourcePhase", "dataType"));
                JsonNode route = object(entry.get("route"), Set.of("competitionCode", "season", "phase", "dataType"));
                var evidence = new ArrayList<CapabilityEvidenceReference>();
                for (JsonNode reference : array(entry, "evidence")) {
                    object(reference, Set.of("logicalId", "observedAt", "sha256"));
                    evidence.add(new CapabilityEvidenceReference(text(reference, "logicalId"),
                            Instant.parse(text(reference, "observedAt")), text(reference, "sha256")));
                }
                JsonNode enabled = entry.get("enabled");
                if (enabled == null || !enabled.isBoolean()) {
                    throw new IllegalArgumentException("enabled must be boolean");
                }
                entries.add(new ProviderCapability(
                        new ProviderCapabilityKey(text(key, "provider"), text(key, "providerCompetitionId"),
                                text(key, "sourceSeason"), text(key, "sourcePhase"),
                                CapabilityDataType.valueOf(text(key, "dataType"))),
                        new CapabilityRouteKey(text(route, "competitionCode"), text(route, "season"),
                                text(route, "phase"), CapabilityDataType.valueOf(text(route, "dataType"))),
                        CapabilityStatus.valueOf(text(entry, "status")),
                        CapabilityAuthorityRole.valueOf(text(entry, "authorityRole")),
                        enabled.booleanValue(), evidence));
            }
            return new ConfiguredProviderCapabilityRegistry(text(root, "registryVersion"),
                    SnapshotHasher.sha256(bytes), entries);
        }
        catch (RuntimeException exception) {
            // Do not echo user-supplied configuration or parser exception content into startup logs.
            throw new IllegalArgumentException("invalid provider capability registry document");
        }
    }

    private static JsonNode object(JsonNode value, Set<String> fields) {
        if (value == null || !value.isObject() || !value.propertyNames().equals(fields)) {
            throw new IllegalArgumentException("registry object has invalid fields");
        }
        return value;
    }

    private static JsonNode array(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("registry field must be an array");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException("registry field must be a string");
        }
        return value.textValue();
    }
}
