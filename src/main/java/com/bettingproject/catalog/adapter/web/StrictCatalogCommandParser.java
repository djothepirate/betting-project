package com.bettingproject.catalog.adapter.web;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.bettingproject.catalog.application.ControlCommandReceipt;
import com.bettingproject.catalog.application.MappingDecisionCommand;
import com.bettingproject.catalog.application.NormalizationReplayCommand;
import com.bettingproject.catalog.application.NormalizationReplaySelector;
import com.bettingproject.identity.domain.MappingDecisionType;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("control-api")
public class StrictCatalogCommandParser {

    private static final Set<String> FORBIDDEN_LOCATION_FIELDS = Set.of(
            "path", "uri", "file", "filepath");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> MAPPING_FIELDS = Set.of(
            "provider", "entityType", "providerEntityId", "season", "phase",
            "decisionType", "canonicalEntityId", "expectedVersion", "justification");
    private static final Set<String> REPLAY_FIELDS = Set.of("snapshotId", "payloadSha256");
    private static final Set<String> RESUME_FIELDS = Set.of("expectedVersion");

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    void requireJsonContentType(String contentType) {
        try {
            if (contentType == null
                    || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) {
                throw CatalogWebException.badRequest("INVALID_CONTENT_TYPE");
            }
        }
        catch (IllegalArgumentException exception) {
            throw CatalogWebException.badRequest("INVALID_CONTENT_TYPE");
        }
    }

    MappingDecisionCommand parseMappingDecision(byte[] body, String idempotencyKey) {
        JsonNode root = readObject(body, MAPPING_FIELDS);
        String provider = requiredText(root, "provider", 64);
        String entityTypeValue = requiredText(root, "entityType", 32);
        ProviderEntityType entityType = enumValue(entityTypeValue, ProviderEntityType.class);
        String providerEntityId = requiredText(root, "providerEntityId", 200);
        String season = optionalText(root, "season", 64);
        String phase = optionalText(root, "phase", 64);
        MappingDecisionType decisionType = enumValue(
                requiredText(root, "decisionType", 16),
                MappingDecisionType.class);
        UUID canonicalEntityId = optionalUuid(root, "canonicalEntityId");
        long expectedVersion = requiredLong(root, "expectedVersion", 0);
        String justification = requiredText(root, "justification", 1_000);
        requireIdempotencyKey(idempotencyKey);

        if ((decisionType == MappingDecisionType.CONFIRM && canonicalEntityId == null)
                || (decisionType == MappingDecisionType.REJECT && canonicalEntityId != null)) {
            throw CatalogWebException.badRequest("INVALID_MAPPING_DECISION");
        }
        if (entityType == ProviderEntityType.SNAPSHOT) {
            throw CatalogWebException.unprocessable("SNAPSHOT_NOT_MAPPABLE");
        }

        ProviderMappingKey key;
        try {
            key = new ProviderMappingKey(
                    provider, entityType, providerEntityId, season, phase);
        }
        catch (IllegalArgumentException exception) {
            throw CatalogWebException.badRequest("INVALID_MAPPING_DECISION");
        }
        return new MappingDecisionCommand(
                key,
                decisionType,
                canonicalEntityId,
                expectedVersion,
                idempotencyKey,
                justification);
    }

    NormalizationReplayCommand parseReplayRequest(byte[] body, String idempotencyKey) {
        JsonNode root = readObject(body, REPLAY_FIELDS);
        boolean byId = root.has("snapshotId") && !root.get("snapshotId").isNull();
        boolean byHash = root.has("payloadSha256") && !root.get("payloadSha256").isNull();
        if (byId == byHash || root.size() != 1) {
            throw CatalogWebException.badRequest("INVALID_REPLAY_SELECTOR");
        }
        requireIdempotencyKey(idempotencyKey);
        NormalizationReplaySelector selector;
        if (byId) {
            selector = new NormalizationReplaySelector.BySnapshotId(
                    requiredUuid(root, "snapshotId"));
        }
        else {
            String sha256 = requiredText(root, "payloadSha256", 64);
            if (!SHA_256.matcher(sha256).matches()) {
                throw CatalogWebException.badRequest("INVALID_PAYLOAD_SHA256");
            }
            selector = new NormalizationReplaySelector.ByPayloadSha256(sha256);
        }
        return new NormalizationReplayCommand(selector, idempotencyKey);
    }

    long parseReplayResume(byte[] body) {
        JsonNode root = readObject(body, RESUME_FIELDS);
        if (root.size() != 1) {
            throw CatalogWebException.badRequest("INVALID_REPLAY_RESUME");
        }
        return requiredLong(root, "expectedVersion", 1);
    }

    private JsonNode readObject(byte[] body, Set<String> allowedFields) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        }
        catch (Exception exception) {
            throw CatalogWebException.badRequest("INVALID_JSON");
        }
        if (root == null || !root.isObject()) {
            throw CatalogWebException.badRequest("INVALID_JSON_OBJECT");
        }
        if (containsForbiddenLocationField(root)) {
            throw CatalogWebException.badRequest("ARBITRARY_PATH_FORBIDDEN");
        }
        Set<String> actual = new HashSet<>();
        actual.addAll(root.propertyNames());
        if (!allowedFields.containsAll(actual)) {
            throw CatalogWebException.badRequest("UNKNOWN_FIELD");
        }
        return root;
    }

    private boolean containsForbiddenLocationField(JsonNode node) {
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                if (FORBIDDEN_LOCATION_FIELDS.contains(name.toLowerCase(Locale.ROOT))
                        || containsForbiddenLocationField(node.get(name))) {
                    return true;
                }
            }
        }
        else if (node.isArray()) {
            for (JsonNode item : node) {
                if (containsForbiddenLocationField(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String requiredText(JsonNode root, String field, int maximumLength) {
        JsonNode value = root.get(field);
        if (value == null || !value.isString()) {
            throw CatalogWebException.badRequest("INVALID_FIELD");
        }
        String raw = value.textValue();
        String normalized = raw.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength
                || containsControl(raw)) {
            throw CatalogWebException.badRequest("INVALID_FIELD");
        }
        return normalized;
    }

    private String optionalText(JsonNode root, String field, int maximumLength) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw CatalogWebException.badRequest("INVALID_FIELD");
        }
        String raw = value.textValue();
        String normalized = raw.trim();
        if (normalized.length() > maximumLength || containsControl(raw)) {
            throw CatalogWebException.badRequest("INVALID_FIELD");
        }
        return normalized;
    }

    private UUID requiredUuid(JsonNode root, String field) {
        UUID value = optionalUuid(root, field);
        if (value == null) {
            throw CatalogWebException.badRequest("INVALID_UUID");
        }
        return value;
    }

    private UUID optionalUuid(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw CatalogWebException.badRequest("INVALID_UUID");
        }
        try {
            return UUID.fromString(value.textValue());
        }
        catch (IllegalArgumentException exception) {
            throw CatalogWebException.badRequest("INVALID_UUID");
        }
    }

    private long requiredLong(JsonNode root, String field, long minimum) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw CatalogWebException.badRequest("INVALID_FIELD");
        }
        long result = value.longValue();
        if (result < minimum) {
            throw CatalogWebException.badRequest("INVALID_FIELD");
        }
        return result;
    }

    private <E extends Enum<E>> E enumValue(String value, Class<E> type) {
        try {
            return Enum.valueOf(type, value);
        }
        catch (IllegalArgumentException exception) {
            throw CatalogWebException.badRequest("INVALID_ENUM_VALUE");
        }
    }

    private void requireIdempotencyKey(String value) {
        try {
            ControlCommandReceipt.requireVisibleAscii(value);
        }
        catch (IllegalArgumentException exception) {
            throw CatalogWebException.badRequest("INVALID_IDEMPOTENCY_KEY");
        }
    }

    private boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
