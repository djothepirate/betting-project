package com.bettingproject.collection.adapter.web.j7;

import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.ACCEPT;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.ACK_MEDIA_TYPE;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.CONTENT_ENCODING;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.CONTENT_LENGTH;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.CONTENT_TYPE;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.DATA_SHA256;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.EXPORT_ID;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.FILE_SHA256;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.IDEMPOTENCY_KEY;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.MAXIMUM_REQUEST_BYTES;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.PROTOCOL_VERSION;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.PROTOCOL_VERSION_HEADER;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.REQUEST_MEDIA_TYPE;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.SCHEMA_ID;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.SCHEMA_RESOURCE;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.SCHEMA_VERSION;
import static com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract.TRANSFER_ENCODING;

import com.bettingproject.collection.application.imports.J7ImportCommand;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pure protocol parser. It performs no network access, persistence or Spring interaction.
 */
public final class StrictJ7ImportParser {

    private final JsonMapper mapper;
    private final Schema schema;

    public StrictJ7ImportParser() {
        mapper = JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder
                        .schemaRegistryConfig(SchemaRegistryConfig.builder()
                                .formatAssertionsEnabled(true)
                                .failFast(false)
                                .build())
                        .schemaLoader(loader -> loader.fetchRemoteResources(false)));
        try (InputStream input = StrictJ7ImportParser.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("J7 import schema is missing from the classpath");
            }
            schema = registry.getSchema(input, InputFormat.JSON);
            if (!SCHEMA_ID.equals(schema.getId())) {
                throw new IllegalStateException("J7 import schema id does not match the contract");
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("J7 import schema cannot be loaded", exception);
        }
    }

    public J7ImportCommand parse(Map<String, List<String>> headers, byte[] content) {
        Map<String, List<String>> safeHeaders = headers == null ? Map.of() : headers;
        String contentType = requiredHeader(safeHeaders, CONTENT_TYPE);
        String accept = requiredHeader(safeHeaders, ACCEPT);
        String lengthHeader = requiredHeader(safeHeaders, CONTENT_LENGTH);
        String protocolVersion = requiredHeader(safeHeaders, PROTOCOL_VERSION_HEADER);
        String exportIdHeader = requiredHeader(safeHeaders, EXPORT_ID);
        String fileSha256 = requiredHeader(safeHeaders, FILE_SHA256);
        String dataSha256 = requiredHeader(safeHeaders, DATA_SHA256);
        String idempotencyKey = requiredHeader(safeHeaders, IDEMPOTENCY_KEY);
        optionalIdentityEncoding(safeHeaders);
        rejectTransferEncoding(safeHeaders);

        if (!REQUEST_MEDIA_TYPE.equals(contentType)) {
            throw failure(J7ImportProtocolError.INVALID_CONTENT_TYPE);
        }
        if (!ACK_MEDIA_TYPE.equals(accept)) {
            throw failure(J7ImportProtocolError.INVALID_ACCEPT);
        }
        if (!PROTOCOL_VERSION.equals(protocolVersion)) {
            throw failure(J7ImportProtocolError.INVALID_PROTOCOL_VERSION);
        }

        int declaredLength = parseContentLength(lengthHeader);
        byte[] exactContent = content == null ? null : content.clone();
        if (exactContent == null || exactContent.length != declaredLength) {
            throw failure(J7ImportProtocolError.CONTENT_LENGTH_MISMATCH);
        }

        UUID exportId = parseCanonicalUuid(exportIdHeader);
        requireSha256(fileSha256);
        requireSha256(dataSha256);
        String expectedIdempotencyKey = "j7:" + exportIdHeader + ":sha256:" + fileSha256;
        if (!J7ImportHttpContract.IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()
                || !expectedIdempotencyKey.equals(idempotencyKey)) {
            throw failure(J7ImportProtocolError.INVALID_IDEMPOTENCY_KEY);
        }

        String actualFileSha256 = sha256Hex(exactContent);
        if (!constantTimeEquals(fileSha256, actualFileSha256)) {
            throw failure(J7ImportProtocolError.INVALID_FILE_HASH);
        }
        requireStrictUtf8(exactContent);
        ObjectNode root = parseJson(exactContent);
        requireCanonicalBytes(root, exactContent);

        ObjectNode manifest = requiredObject(root, "manifest");
        ObjectNode data = requiredObject(root, "data");
        String schemaId = requiredText(manifest, "schemaId");
        String schemaVersion = requiredText(manifest, "schemaVersion");
        if (!SCHEMA_ID.equals(schemaId)) {
            throw failure(J7ImportProtocolError.INVALID_SCHEMA_ID);
        }
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw failure(J7ImportProtocolError.INVALID_SCHEMA_VERSION);
        }
        String manifestExportId = requiredText(manifest, "exportId");
        parseCanonicalUuid(manifestExportId);
        if (!exportIdHeader.equals(manifestExportId)) {
            throw failure(J7ImportProtocolError.IDENTITY_MISMATCH);
        }
        String status = requiredText(requiredObject(manifest, "validation"), "status");
        if (!"HUMAN_VALIDATED".equals(status)) {
            throw failure(J7ImportProtocolError.INVALID_VALIDATION_STATUS);
        }
        validateSchema(root);

        ObjectNode identity = requiredObject(data, "identity");
        UUID canonicalEventId = parseCanonicalEventUuid(requiredText(identity, "canonicalEventId"));
        long providerEventId = requiredPositiveLong(identity, "providerEventId");
        Instant generatedAt = requiredInstant(manifest, "generatedAt");
        String generatorVersion = requiredText(manifest, "generatorVersion");
        if (!generatorVersion.matches("[A-Za-z0-9._+-]{1,64}")) {
            throw failure(J7ImportProtocolError.INVALID_GENERATOR_VERSION);
        }
        if (!"LATEST_AVAILABLE".equals(requiredText(manifest, "selectionMode"))) {
            throw failure(J7ImportProtocolError.INVALID_SCHEMA);
        }
        Instant validationDecidedAt = requiredInstant(
                requiredObject(manifest, "validation"),
                "decidedAt");
        if (validationDecidedAt.isBefore(generatedAt)) {
            throw failure(J7ImportProtocolError.INVALID_VALIDATION_TIME);
        }

        String manifestDataSha256 = requiredText(manifest, "dataSha256");
        String actualDataSha256 = sha256Hex(writeCompact(data));
        if (!constantTimeEquals(dataSha256, manifestDataSha256)
                || !constantTimeEquals(dataSha256, actualDataSha256)) {
            throw failure(J7ImportProtocolError.INVALID_DATA_HASH);
        }
        String manifestSourceSetSha256 = requiredText(manifest, "sourceSetSha256");
        requireSha256(manifestSourceSetSha256);
        String actualSourceSetSha256 = sha256Hex(writeCompact(manifest.required("sources")));
        if (!constantTimeEquals(manifestSourceSetSha256, actualSourceSetSha256)) {
            throw failure(J7ImportProtocolError.INVALID_SOURCE_SET_HASH);
        }

        return new J7ImportCommand(
                idempotencyKey,
                exportId,
                fileSha256,
                dataSha256,
                schemaId,
                schemaVersion,
                canonicalEventId,
                providerEventId,
                generatedAt,
                generatorVersion,
                manifestSourceSetSha256,
                status,
                validationDecidedAt,
                exactContent);
    }

    private String requiredHeader(Map<String, List<String>> headers, String expectedName) {
        List<String> values = valuesFor(headers, expectedName);
        if (values.isEmpty()) {
            throw failure(J7ImportProtocolError.MISSING_HEADER);
        }
        if (values.size() != 1 || values.getFirst() == null) {
            throw failure(J7ImportProtocolError.DUPLICATE_HEADER);
        }
        return values.getFirst();
    }

    private void optionalIdentityEncoding(Map<String, List<String>> headers) {
        List<String> values = valuesFor(headers, CONTENT_ENCODING);
        if (values.size() > 1) {
            throw failure(J7ImportProtocolError.DUPLICATE_HEADER);
        }
        if (!values.isEmpty() && !"identity".equals(values.getFirst())) {
            throw failure(J7ImportProtocolError.INVALID_CONTENT_ENCODING);
        }
    }

    private void rejectTransferEncoding(Map<String, List<String>> headers) {
        List<String> values = valuesFor(headers, TRANSFER_ENCODING);
        if (values.size() > 1) {
            throw failure(J7ImportProtocolError.DUPLICATE_HEADER);
        }
        if (!values.isEmpty()) {
            throw failure(J7ImportProtocolError.INVALID_TRANSFER_ENCODING);
        }
    }

    private List<String> valuesFor(Map<String, List<String>> headers, String expectedName) {
        List<String> values = new ArrayList<>();
        headers.forEach((name, headerValues) -> {
            if (name != null && name.equalsIgnoreCase(expectedName)) {
                if (headerValues == null) {
                    values.add(null);
                }
                else {
                    values.addAll(headerValues);
                }
            }
        });
        return values;
    }

    private int parseContentLength(String value) {
        if (value.isEmpty() || value.length() > 7
                || value.chars().anyMatch(character -> character < '0' || character > '9')) {
            throw failure(J7ImportProtocolError.INVALID_CONTENT_LENGTH);
        }
        try {
            int length = Integer.parseInt(value);
            if (length < 1 || length > MAXIMUM_REQUEST_BYTES) {
                throw failure(J7ImportProtocolError.INVALID_CONTENT_LENGTH);
            }
            return length;
        }
        catch (NumberFormatException exception) {
            throw failure(J7ImportProtocolError.INVALID_CONTENT_LENGTH);
        }
    }

    private UUID parseCanonicalUuid(String value) {
        if (!J7ImportHttpContract.UUID_PATTERN.matcher(value).matches()) {
            throw failure(J7ImportProtocolError.INVALID_EXPORT_ID);
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw failure(J7ImportProtocolError.INVALID_EXPORT_ID);
            }
            return parsed;
        }
        catch (IllegalArgumentException exception) {
            throw failure(J7ImportProtocolError.INVALID_EXPORT_ID);
        }
    }

    private UUID parseCanonicalEventUuid(String value) {
        try {
            return parseCanonicalUuid(value);
        }
        catch (J7ImportProtocolException exception) {
            throw failure(J7ImportProtocolError.INVALID_CANONICAL_EVENT_ID);
        }
    }

    private void requireSha256(String value) {
        if (!J7ImportHttpContract.SHA_256_PATTERN.matcher(value).matches()) {
            throw failure(J7ImportProtocolError.INVALID_SHA256);
        }
    }

    private void requireStrictUtf8(byte[] content) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            if (decoded.indexOf('\r') >= 0) {
                throw failure(J7ImportProtocolError.NON_CANONICAL_CONTENT);
            }
        }
        catch (CharacterCodingException exception) {
            throw failure(J7ImportProtocolError.INVALID_UTF8);
        }
    }

    private ObjectNode parseJson(byte[] content) {
        try {
            JsonNode parsed = mapper.readTree(content);
            if (!(parsed instanceof ObjectNode root)) {
                throw failure(J7ImportProtocolError.INVALID_JSON);
            }
            return root;
        }
        catch (J7ImportProtocolException exception) {
            throw exception;
        }
        catch (JacksonException exception) {
            throw failure(J7ImportProtocolError.INVALID_JSON);
        }
    }

    private void validateSchema(ObjectNode root) {
        if (!schema.validate(root).isEmpty()) {
            throw failure(J7ImportProtocolError.INVALID_SCHEMA);
        }
    }

    private void requireCanonicalBytes(ObjectNode root, byte[] content) {
        byte[] compact = writeCompact(root);
        byte[] expected = Arrays.copyOf(compact, compact.length + 1);
        expected[expected.length - 1] = (byte) '\n';
        if (!MessageDigest.isEqual(expected, content)) {
            throw failure(J7ImportProtocolError.NON_CANONICAL_CONTENT);
        }
    }

    private byte[] writeCompact(JsonNode node) {
        try {
            return mapper.writeValueAsBytes(node);
        }
        catch (JacksonException exception) {
            throw failure(J7ImportProtocolError.INVALID_JSON);
        }
    }

    private ObjectNode requiredObject(ObjectNode parent, String name) {
        JsonNode child = parent.get(name);
        if (child instanceof ObjectNode objectNode) {
            return objectNode;
        }
        throw failure(J7ImportProtocolError.INVALID_SCHEMA);
    }

    private String requiredText(ObjectNode parent, String name) {
        JsonNode child = parent.get(name);
        if (child != null && child.isString()) {
            return child.stringValue();
        }
        throw failure(J7ImportProtocolError.INVALID_SCHEMA);
    }

    private long requiredPositiveLong(ObjectNode parent, String name) {
        JsonNode child = parent.get(name);
        if (child == null || !child.isIntegralNumber() || !child.canConvertToLong()) {
            throw failure(J7ImportProtocolError.INVALID_PROVIDER_EVENT_ID);
        }
        long value = child.longValue();
        if (value < 1) {
            throw failure(J7ImportProtocolError.INVALID_PROVIDER_EVENT_ID);
        }
        return value;
    }

    private Instant requiredInstant(ObjectNode parent, String name) {
        String value = requiredText(parent, name);
        try {
            return Instant.parse(value);
        }
        catch (DateTimeException exception) {
            throw failure(J7ImportProtocolError.INVALID_TIMESTAMP);
        }
    }

    private String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private J7ImportProtocolException failure(J7ImportProtocolError error) {
        return new J7ImportProtocolException(error);
    }
}
