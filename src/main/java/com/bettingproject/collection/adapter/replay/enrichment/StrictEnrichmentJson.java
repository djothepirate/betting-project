package com.bettingproject.collection.adapter.replay.enrichment;

import com.bettingproject.collection.application.enrichment.EnrichmentParseException;
import com.bettingproject.enrichment.domain.EnrichmentObservationState;
import com.bettingproject.enrichment.domain.ObservedScalar;
import com.bettingproject.enrichment.domain.ObservedScalarType;
import java.time.Instant;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

/** Local strict JSON utilities shared by offline enrichment parsers. */
final class StrictEnrichmentJson {

    static final int MAX_PAYLOAD_BYTES = 5 * 1024 * 1024;
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private StrictEnrichmentJson() {
    }

    static JsonNode parse(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw incompatible();
        }
        try {
            JsonNode root = MAPPER.readTree(payload);
            if (root == null) {
                throw incompatible();
            }
            return root;
        } catch (EnrichmentParseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw incompatible();
        }
    }

    static JsonNode requireArray(JsonNode value) {
        if (value == null || !value.isArray()) {
            throw incompatible();
        }
        return value;
    }

    static JsonNode requireObject(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw incompatible();
        }
        return value;
    }

    static String requiredText(JsonNode object, String field, int maxLength) {
        JsonNode value = object.get(field);
        if (value == null || !value.isString()) {
            throw incompatible();
        }
        return checkedText(value.textValue(), maxLength);
    }

    static String checkedText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw incompatible();
        }
        return value;
    }

    static String requiredId(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw incompatible();
        }
        return Long.toString(value.longValue());
    }

    static Instant requiredInstant(JsonNode object, String field) {
        String value = requiredText(object, field, 64);
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw incompatible();
        }
    }

    static ObservedScalar nestedScalar(JsonNode object, String parentField, String field) {
        JsonNode parent = object.get(parentField);
        if (parent == null) {
            return ObservedScalar.missing();
        }
        if (parent.isNull()) {
            return ObservedScalar.nullValue();
        }
        requireObject(parent);
        return scalar(parent, field);
    }

    static JsonNode nestedObject(JsonNode object, String parentField, String field) {
        JsonNode parent = object.get(parentField);
        if (parent == null) {
            return null;
        }
        if (parent.isNull()) {
            return NullNode.getInstance();
        }
        requireObject(parent);
        JsonNode child = parent.get(field);
        if (child == null || child.isNull()) {
            return child;
        }
        return requireObject(child);
    }

    static ObservedScalar textScalar(JsonNode object, String field) {
        ObservedScalar result = scalar(object, field);
        if (result.state() == EnrichmentObservationState.AVAILABLE
                && result.type() != ObservedScalarType.TEXT) {
            throw incompatible();
        }
        return result;
    }

    static ObservedScalar optionalPositiveIdScalar(JsonNode object, String field) {
        ObservedScalar result = scalar(object, field);
        if (result.state() == EnrichmentObservationState.AVAILABLE
                && (result.type() != ObservedScalarType.INTEGER
                || result.decimalValue().signum() <= 0)) {
            throw incompatible();
        }
        return result;
    }

    static ObservedScalar scalar(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return scalar(value, value == null);
    }

    static ObservedScalar scalar(JsonNode value, boolean missing) {
        if (missing) {
            return ObservedScalar.missing();
        }
        if (value == null || value.isNull()) {
            return ObservedScalar.nullValue();
        }
        if (value.isIntegralNumber()) {
            return ObservedScalar.of(ObservedScalarType.INTEGER, value.asText());
        }
        if (value.isNumber()) {
            return ObservedScalar.of(ObservedScalarType.DECIMAL, value.asText());
        }
        if (value.isString()) {
            String text = value.textValue();
            if (text.length() > 256 || text.codePoints().anyMatch(Character::isISOControl)) {
                throw incompatible();
            }
            return ObservedScalar.of(ObservedScalarType.TEXT, text);
        }
        if (value.isBoolean()) {
            return ObservedScalar.of(ObservedScalarType.BOOLEAN, value.asText());
        }
        throw incompatible();
    }

    static EnrichmentObservationState arrayFieldState(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null) {
            return EnrichmentObservationState.NOT_PRESENT;
        }
        if (value.isNull()) {
            return EnrichmentObservationState.NULL_VALUE;
        }
        if (!value.isArray()) {
            throw incompatible();
        }
        return value.isEmpty() ? EnrichmentObservationState.EMPTY : EnrichmentObservationState.AVAILABLE;
    }

    static EnrichmentParseException incompatible() {
        return new EnrichmentParseException("INCOMPATIBLE_PAYLOAD");
    }
}
