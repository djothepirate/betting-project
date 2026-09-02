package com.bettingproject.collection.adapter.web.j7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bettingproject.collection.application.imports.J7ImportCommand;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

class StrictJ7ImportParserTest {

    private final StrictJ7ImportParser parser = new StrictJ7ImportParser();

    @Test
    void parsesCanonicalHumanValidatedArtifactAndPreservesExactBytesDefensively() {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        byte[] submitted = artifact.body();

        J7ImportCommand command = parser.parse(artifact.headers(), submitted);

        assertThat(command.exportId().toString()).isEqualTo(J7ImportTestArtifact.EXPORT_ID);
        assertThat(command.schemaId()).isEqualTo(J7ImportHttpContract.SCHEMA_ID);
        assertThat(command.schemaVersion()).isEqualTo(J7ImportHttpContract.SCHEMA_VERSION);
        assertThat(command.canonicalEventId().toString())
                .isEqualTo(J7ImportTestArtifact.CANONICAL_EVENT_ID);
        assertThat(command.providerEventId()).isEqualTo(42L);
        assertThat(command.generatedAt()).isEqualTo(Instant.parse("2026-09-02T00:00:00Z"));
        assertThat(command.generatorVersion()).isEqualTo("int001-test-v1");
        assertThat(command.sourceSetSha256()).hasSize(64);
        assertThat(command.validationStatus()).isEqualTo("HUMAN_VALIDATED");
        assertThat(command.validationDecidedAt())
                .isEqualTo(Instant.parse("2026-09-02T00:01:00Z"));
        assertThat(command.fileSha256()).isEqualTo(J7ImportTestArtifact.sha256(artifact.body()));
        assertThat(command.content()).containsExactly(artifact.body());
        submitted[0] ^= 1;
        byte[] leaked = command.content();
        leaked[0] ^= 1;
        assertThat(command.content()).containsExactly(artifact.body());
    }

    @Test
    void acceptsCaseInsensitiveHeaderNamesButRejectsDuplicateNamesAcrossCasing() {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        Map<String, List<String>> lowerCase = new LinkedHashMap<>();
        artifact.headers().forEach((name, values) -> lowerCase.put(name.toLowerCase(), values));
        assertThat(parser.parse(lowerCase, artifact.body()).exportId().toString())
                .isEqualTo(J7ImportTestArtifact.EXPORT_ID);

        Map<String, List<String>> duplicate = artifact.mutableHeaders();
        duplicate.put("x-j7-export-id", List.of(J7ImportTestArtifact.EXPORT_ID));
        assertFailure(duplicate, artifact.body(), J7ImportProtocolError.DUPLICATE_HEADER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            J7ImportHttpContract.CONTENT_TYPE,
            J7ImportHttpContract.ACCEPT,
            J7ImportHttpContract.CONTENT_LENGTH,
            J7ImportHttpContract.IDEMPOTENCY_KEY,
            J7ImportHttpContract.PROTOCOL_VERSION_HEADER,
            J7ImportHttpContract.EXPORT_ID,
            J7ImportHttpContract.FILE_SHA256,
            J7ImportHttpContract.DATA_SHA256
    })
    void rejectsEachMissingOrRepeatedMandatoryHeader(String name) {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        Map<String, List<String>> missing = artifact.mutableHeaders();
        missing.remove(name);
        assertFailure(missing, artifact.body(), J7ImportProtocolError.MISSING_HEADER);

        Map<String, List<String>> repeated = artifact.mutableHeaders();
        repeated.put(name, List.of(repeated.get(name).getFirst(), repeated.get(name).getFirst()));
        assertFailure(repeated, artifact.body(), J7ImportProtocolError.DUPLICATE_HEADER);
    }

    @Test
    void rejectsWrongMediaProtocolEncodingAndTransferValues() {
        assertHeaderFailure(
                J7ImportHttpContract.CONTENT_TYPE,
                "application/json",
                J7ImportProtocolError.INVALID_CONTENT_TYPE);
        assertHeaderFailure(
                J7ImportHttpContract.ACCEPT,
                "*/*",
                J7ImportProtocolError.INVALID_ACCEPT);
        assertHeaderFailure(
                J7ImportHttpContract.PROTOCOL_VERSION_HEADER,
                "1.1",
                J7ImportProtocolError.INVALID_PROTOCOL_VERSION);
        assertHeaderFailure(
                J7ImportHttpContract.CONTENT_ENCODING,
                "gzip",
                J7ImportProtocolError.INVALID_CONTENT_ENCODING);
        assertHeaderFailure(
                J7ImportHttpContract.TRANSFER_ENCODING,
                "chunked",
                J7ImportProtocolError.INVALID_TRANSFER_ENCODING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "5242881", "-1", "+1", " 1", "1 ", "99999999"})
    void rejectsInvalidContentLengthSyntaxAndBounds(String value) {
        assertHeaderFailure(
                J7ImportHttpContract.CONTENT_LENGTH,
                value,
                J7ImportProtocolError.INVALID_CONTENT_LENGTH);
    }

    @Test
    void rejectsBodyLengthThatDoesNotMatchTheSingleDeclaredLength() {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        Map<String, List<String>> headers = artifact.mutableHeaders();
        headers.put(J7ImportHttpContract.CONTENT_LENGTH,
                List.of(Integer.toString(artifact.body().length + 1)));
        assertFailure(headers, artifact.body(), J7ImportProtocolError.CONTENT_LENGTH_MISMATCH);
        assertFailure(artifact.headers(), null, J7ImportProtocolError.CONTENT_LENGTH_MISMATCH);
    }

    @Test
    void rejectsNonCanonicalUuidHashesAndDerivedIdempotencyKey() {
        assertHeaderFailure(
                J7ImportHttpContract.EXPORT_ID,
                J7ImportTestArtifact.EXPORT_ID.toUpperCase(),
                J7ImportProtocolError.INVALID_EXPORT_ID);
        assertHeaderFailure(
                J7ImportHttpContract.EXPORT_ID,
                "00000000-0000-0000-0000-000000000000",
                J7ImportProtocolError.INVALID_EXPORT_ID);
        assertHeaderFailure(
                J7ImportHttpContract.FILE_SHA256,
                "A".repeat(64),
                J7ImportProtocolError.INVALID_SHA256);
        assertHeaderFailure(
                J7ImportHttpContract.DATA_SHA256,
                "a".repeat(63),
                J7ImportProtocolError.INVALID_SHA256);
        assertHeaderFailure(
                J7ImportHttpContract.IDEMPOTENCY_KEY,
                "j7:" + J7ImportTestArtifact.EXPORT_ID + ":sha256:" + "0".repeat(64),
                J7ImportProtocolError.INVALID_IDEMPOTENCY_KEY);
    }

    @Test
    void hashesTheExactSubmittedBytesBeforeParsing() {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        byte[] changed = artifact.body();
        changed[changed.length - 2] ^= 1;
        Map<String, List<String>> headers = artifact.mutableHeaders();
        headers.put(J7ImportHttpContract.CONTENT_LENGTH,
                List.of(Integer.toString(changed.length)));
        assertFailure(headers, changed, J7ImportProtocolError.INVALID_FILE_HASH);
    }

    @Test
    void rejectsMalformedUtf8DuplicateKeysAndTrailingTokens() {
        J7ImportTestArtifact.Artifact valid = J7ImportTestArtifact.valid();
        byte[] malformedUtf8 = {(byte) 0xc3, (byte) 0x28};
        assertFailure(
                withExactBodyHeaders(valid, malformedUtf8),
                malformedUtf8,
                J7ImportProtocolError.INVALID_UTF8);

        String validText = new String(valid.body(), StandardCharsets.UTF_8);
        String duplicateText = validText.replaceFirst(
                "\\{\\\"manifest\\\":\\{",
                "{\\\"manifest\\\":{\\\"exportId\\\":\\\""
                        + J7ImportTestArtifact.EXPORT_ID + "\\\",");
        byte[] duplicate = J7ImportTestArtifact.bytes(duplicateText);
        assertFailure(
                withExactBodyHeaders(valid, duplicate),
                duplicate,
                J7ImportProtocolError.INVALID_JSON);

        byte[] trailing = J7ImportTestArtifact.bytes(validText + "{}");
        assertFailure(
                withExactBodyHeaders(valid, trailing),
                trailing,
                J7ImportProtocolError.INVALID_JSON);
    }

    @Test
    void rejectsNonCanonicalWhitespaceMissingLfAndCrLf() {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        ObjectNode root = artifact.root();
        String pretty;
        try {
            pretty = tools.jackson.databind.json.JsonMapper.builder().build()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root) + "\n";
        }
        catch (tools.jackson.core.JacksonException exception) {
            throw new AssertionError(exception);
        }
        byte[] prettyBytes = J7ImportTestArtifact.bytes(pretty);
        assertFailure(
                withExactBodyHeaders(artifact, prettyBytes),
                prettyBytes,
                J7ImportProtocolError.NON_CANONICAL_CONTENT);

        byte[] withoutLf = Arrays.copyOf(artifact.body(), artifact.body().length - 1);
        assertFailure(
                withExactBodyHeaders(artifact, withoutLf),
                withoutLf,
                J7ImportProtocolError.NON_CANONICAL_CONTENT);

        byte[] crlf = J7ImportTestArtifact.bytes(
                new String(withoutLf, StandardCharsets.UTF_8) + "\r\n");
        assertFailure(
                withExactBodyHeaders(artifact, crlf),
                crlf,
                J7ImportProtocolError.NON_CANONICAL_CONTENT);
    }

    @Test
    void rejectsUnknownFieldsAndInvalidFormatAssertionsThroughDraft202012Schema() {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        ObjectNode unknown = artifact.root();
        unknown.put("unexpected", true);
        assertRootFailure(unknown, J7ImportProtocolError.INVALID_SCHEMA);

        ObjectNode badDate = artifact.root();
        ((ObjectNode) badDate.get("manifest")).put("generatedAt", "not-an-instant");
        assertRootFailure(badDate, J7ImportProtocolError.INVALID_SCHEMA);

        ObjectNode oversizedId = artifact.root();
        ((ObjectNode) ((ObjectNode) oversizedId.get("data")).get("identity"))
                .put("providerEventId", new java.math.BigInteger("9223372036854775808"));
        assertRootFailure(oversizedId, J7ImportProtocolError.INVALID_SCHEMA);
    }

    @Test
    void exposesStrictDurableMetadataOnlyAfterSchemaValidation() {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        ObjectNode invalidProviderId = artifact.root();
        ((ObjectNode) ((ObjectNode) invalidProviderId.get("data")).get("identity"))
                .put("providerEventId", 0L);
        assertRootFailure(invalidProviderId, J7ImportProtocolError.INVALID_SCHEMA);

        ObjectNode nonCanonicalEventId = artifact.root();
        ((ObjectNode) ((ObjectNode) nonCanonicalEventId.get("data")).get("identity"))
                .put("canonicalEventId", J7ImportTestArtifact.CANONICAL_EVENT_ID.toUpperCase());
        assertRootFailure(
                nonCanonicalEventId,
                J7ImportProtocolError.INVALID_CANONICAL_EVENT_ID);

        ObjectNode decisionBeforeGeneration = artifact.root();
        ObjectNode validation = (ObjectNode) ((ObjectNode) decisionBeforeGeneration.get("manifest"))
                .get("validation");
        validation.put("decidedAt", "2026-09-01T23:59:59Z");
        assertRootFailure(
                decisionBeforeGeneration,
                J7ImportProtocolError.INVALID_VALIDATION_TIME);
    }

    @Test
    void rejectsWrongSchemaVersionValidationStatusAndBodyIdentity() {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        ObjectNode wrongSchema = artifact.root();
        ((ObjectNode) wrongSchema.get("manifest")).put("schemaId", "urn:wrong");
        assertRootFailure(wrongSchema, J7ImportProtocolError.INVALID_SCHEMA_ID);

        ObjectNode wrongVersion = artifact.root();
        ((ObjectNode) wrongVersion.get("manifest")).put("schemaVersion", "2.0.0");
        assertRootFailure(wrongVersion, J7ImportProtocolError.INVALID_SCHEMA_VERSION);

        ObjectNode pending = artifact.root();
        ObjectNode validation = (ObjectNode) ((ObjectNode) pending.get("manifest")).get("validation");
        validation.put("status", "COHERENCE_CHECKED");
        validation.putNull("decidedAt");
        assertRootFailure(pending, J7ImportProtocolError.INVALID_VALIDATION_STATUS);

        ObjectNode otherExport = artifact.root();
        ((ObjectNode) otherExport.get("manifest"))
                .put("exportId", "33333333-3333-4333-8333-333333333333");
        J7ImportTestArtifact.Artifact changed = J7ImportTestArtifact.fromRoot(otherExport);
        Map<String, List<String>> originalIdentityHeaders = changed.mutableHeaders();
        originalIdentityHeaders.put(J7ImportHttpContract.EXPORT_ID,
                List.of(J7ImportTestArtifact.EXPORT_ID));
        originalIdentityHeaders.put(J7ImportHttpContract.IDEMPOTENCY_KEY,
                List.of("j7:" + J7ImportTestArtifact.EXPORT_ID + ":sha256:"
                        + originalIdentityHeaders.get(J7ImportHttpContract.FILE_SHA256).getFirst()));
        assertFailure(
                originalIdentityHeaders,
                changed.body(),
                J7ImportProtocolError.IDENTITY_MISMATCH);
    }

    @Test
    void rejectsManifestHeaderAndRecomputedDataHashDivergence() {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        Map<String, List<String>> wrongHeader = artifact.mutableHeaders();
        wrongHeader.put(J7ImportHttpContract.DATA_SHA256, List.of("e".repeat(64)));
        assertFailure(wrongHeader, artifact.body(), J7ImportProtocolError.INVALID_DATA_HASH);

        ObjectNode changedData = artifact.root();
        ObjectNode identity = (ObjectNode) ((ObjectNode) changedData.get("data")).get("identity");
        identity.put("providerEventId", 43L);
        J7ImportTestArtifact.Artifact changed = J7ImportTestArtifact.fromRoot(changedData);
        assertFailure(changed.headers(), changed.body(), J7ImportProtocolError.INVALID_DATA_HASH);
    }

    @Test
    void rejectsSourceSetHashThatDoesNotCoverTheExactSourcesArray() {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        ObjectNode changed = artifact.root();
        ObjectNode firstSource = (ObjectNode) ((ObjectNode) changed.get("manifest"))
                .get("sources").get(0);
        firstSource.put("observationId", 2L);
        J7ImportTestArtifact.Artifact changedArtifact = J7ImportTestArtifact.fromRoot(changed);
        assertFailure(
                changedArtifact.headers(),
                changedArtifact.body(),
                J7ImportProtocolError.INVALID_SOURCE_SET_HASH);
    }

    private void assertHeaderFailure(
            String name,
            String value,
            J7ImportProtocolError expected) {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        Map<String, List<String>> headers = artifact.mutableHeaders();
        headers.put(name, List.of(value));
        assertFailure(headers, artifact.body(), expected);
    }

    private void assertRootFailure(ObjectNode root, J7ImportProtocolError expected) {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.fromRoot(root);
        assertFailure(artifact.headers(), artifact.body(), expected);
    }

    private Map<String, List<String>> withExactBodyHeaders(
            J7ImportTestArtifact.Artifact identity,
            byte[] body) {
        Map<String, List<String>> headers = identity.mutableHeaders();
        String fileSha = J7ImportTestArtifact.sha256(body);
        headers.put(J7ImportHttpContract.CONTENT_LENGTH, List.of(Integer.toString(body.length)));
        headers.put(J7ImportHttpContract.FILE_SHA256, List.of(fileSha));
        headers.put(J7ImportHttpContract.IDEMPOTENCY_KEY,
                List.of("j7:" + J7ImportTestArtifact.EXPORT_ID + ":sha256:" + fileSha));
        return headers;
    }

    private void assertFailure(
            Map<String, List<String>> headers,
            byte[] body,
            J7ImportProtocolError expected) {
        assertThatThrownBy(() -> parser.parse(headers, body))
                .isInstanceOfSatisfying(J7ImportProtocolException.class,
                        exception -> assertThat(exception.error()).isEqualTo(expected));
    }
}
