package com.bettingproject.collection.adapter.replay.calendar;

import com.bettingproject.collection.application.DiscoveredTeam;
import com.bettingproject.collection.application.calendar.CalendarPageParseException;
import com.bettingproject.collection.application.calendar.CalendarPageRequest;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import java.time.Instant;
import java.time.ZoneOffset;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class NativeCalendarFields {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private NativeCalendarFields() {
    }

    static JsonNode root(byte[] body, CalendarPageRequest request, Instant observedAt, String provider) {
        if (request == null || observedAt == null || body == null || body.length == 0
                || body.length > 5 * 1024 * 1024
                || !provider.equals(request.capability().provider())
                || request.capability().dataType() != CapabilityDataType.CALENDAR) {
            throw incompatible();
        }
        return object(MAPPER.readTree(body));
    }

    static JsonNode object(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw incompatible();
        }
        return value;
    }

    static JsonNode array(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw incompatible();
        }
        return value;
    }

    static String text(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isString() || value.textValue().isBlank()) {
            throw incompatible();
        }
        return value.textValue();
    }

    static String optionalText(JsonNode parent, String field) {
        if (parent == null || parent.isNull()) {
            return null;
        }
        object(parent);
        JsonNode value = parent.get(field);
        return value == null || value.isNull() ? null : text(parent, field);
    }

    static long nonNegativeInteger(JsonNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw incompatible();
        }
        return value.longValue();
    }

    static String id(JsonNode parent, String field) {
        long value = nonNegativeInteger(parent, field);
        if (value == 0) {
            throw incompatible();
        }
        return Long.toString(value);
    }

    static void exact(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw incompatible();
        }
    }

    static Instant kickoff(JsonNode fixture, String field, CalendarPageRequest request) {
        Instant instant = Instant.parse(text(fixture, field));
        if (!instant.atOffset(ZoneOffset.UTC).toLocalDate().equals(request.date())) {
            throw incompatible();
        }
        return instant;
    }

    static DiscoveredTeam team(JsonNode fixture, String field) {
        JsonNode value = object(fixture.get(field));
        // These endpoints do not guarantee each team's own country. The competition is not a substitute.
        return new DiscoveredTeam(id(value, "id"), text(value, "name"), null);
    }

    static void distinct(DiscoveredTeam home, DiscoveredTeam away) {
        if (home.providerTeamId().equals(away.providerTeamId())) {
            throw incompatible();
        }
    }

    static CalendarPageParseException incompatible() {
        return new CalendarPageParseException("INCOMPATIBLE");
    }

    static CalendarPageParseException incomplete() {
        return new CalendarPageParseException("INCOMPLETE_PAGINATION");
    }
}
