package com.bettingproject.catalog.adapter.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import com.bettingproject.catalog.application.AttemptKeysetAnchor;
import com.bettingproject.catalog.application.CorrelationKeysetAnchor;
import com.bettingproject.catalog.application.TimestampKeysetAnchor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("control-api")
public class CatalogCursorCodec {

    private static final String VERSION = "v1";
    private static final int MAXIMUM_CURSOR_LENGTH = 2_048;
    private static final Pattern SCOPE = Pattern.compile("[a-z0-9-]{1,80}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    String encodeTimestamp(
            String scope,
            String filterSha256,
            TimestampKeysetAnchor anchor) {
        return encode(scope, filterSha256, "timestamp", anchor.timestamp().toString(), anchor.id());
    }

    TimestampKeysetAnchor decodeTimestamp(
            String cursor,
            String scope,
            String filterSha256) {
        String[] fields = decode(cursor, scope, filterSha256, "timestamp");
        try {
            return new TimestampKeysetAnchor(Instant.parse(fields[4]), UUID.fromString(fields[5]));
        }
        catch (DateTimeParseException | IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    String encodeAttempt(
            String scope,
            String filterSha256,
            AttemptKeysetAnchor anchor) {
        return encode(
                scope,
                filterSha256,
                "attempt",
                Integer.toString(anchor.attemptNumber()),
                anchor.id());
    }

    AttemptKeysetAnchor decodeAttempt(
            String cursor,
            String scope,
            String filterSha256) {
        String[] fields = decode(cursor, scope, filterSha256, "attempt");
        try {
            return new AttemptKeysetAnchor(Integer.parseInt(fields[4]), UUID.fromString(fields[5]));
        }
        catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    String encodeCorrelation(
            String scope,
            String filterSha256,
            CorrelationKeysetAnchor anchor) {
        return encode(
                scope,
                filterSha256,
                "correlation",
                anchor.createdAt().toString(),
                anchor.targetId());
    }

    CorrelationKeysetAnchor decodeCorrelation(
            String cursor,
            String scope,
            String filterSha256) {
        String[] fields = decode(cursor, scope, filterSha256, "correlation");
        try {
            return new CorrelationKeysetAnchor(
                    Instant.parse(fields[4]),
                    UUID.fromString(fields[5]));
        }
        catch (DateTimeParseException | IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private String encode(
            String scope,
            String filterSha256,
            String anchorType,
            String firstAnchorValue,
            UUID id) {
        validateBinding(scope, filterSha256);
        String payload = String.join(
                "\n",
                VERSION,
                scope,
                filterSha256,
                anchorType,
                firstAnchorValue,
                id.toString());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String[] decode(
            String cursor,
            String scope,
            String filterSha256,
            String expectedAnchorType) {
        validateBinding(scope, filterSha256);
        if (cursor == null || cursor.isBlank() || cursor.length() > MAXIMUM_CURSOR_LENGTH
                || cursor.indexOf('=') >= 0) {
            throw invalidCursor();
        }
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(cursor);
        }
        catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
        String[] fields = new String(decoded, StandardCharsets.UTF_8).split("\n", -1);
        if (fields.length != 6
                || !VERSION.equals(fields[0])
                || !scope.equals(fields[1])
                || !filterSha256.equals(fields[2])
                || !expectedAnchorType.equals(fields[3])) {
            throw invalidCursor();
        }
        return fields;
    }

    private void validateBinding(String scope, String filterSha256) {
        if (scope == null || !SCOPE.matcher(scope).matches()
                || filterSha256 == null || !SHA_256.matcher(filterSha256).matches()) {
            throw new IllegalArgumentException("cursor binding is invalid");
        }
    }

    private CatalogWebException invalidCursor() {
        return CatalogWebException.badRequest("INVALID_CURSOR");
    }
}
