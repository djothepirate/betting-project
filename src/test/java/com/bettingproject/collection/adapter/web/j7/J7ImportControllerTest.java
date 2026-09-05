package com.bettingproject.collection.adapter.web.j7;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.adapter.configuration.J7ClientCertificateFilter;
import com.bettingproject.collection.application.imports.J7AcceptedOutboxEvent;
import com.bettingproject.collection.application.imports.J7ImportAuditEntry;
import com.bettingproject.collection.application.imports.J7ImportAuditReason;
import com.bettingproject.collection.application.imports.J7ImportCommand;
import com.bettingproject.collection.application.imports.J7ImportResult;
import com.bettingproject.collection.application.imports.J7ImportRetentionPolicy;
import com.bettingproject.collection.application.imports.J7ImportService;
import com.bettingproject.collection.application.imports.J7ImportStore;
import com.bettingproject.collection.application.imports.StoredJ7Import;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class J7ImportControllerTest {

    private static final String CERTIFICATE_SHA256 = "f".repeat(64);
    private static final Instant RECEIVED_AT = Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void returnsStrictImportedAndDuplicateAcknowledgements() throws Exception {
        StubService service = new StubService();
        MockMvc mvc = mvc(service);
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();

        byte[] importedBody = mvc.perform(validRequest(artifact))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(J7ImportHttpContract.ACK_MEDIA_TYPE))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.protocolVersion").value("1.0"))
                .andExpect(jsonPath("$.status").value("IMPORTED"))
                .andExpect(jsonPath("$.exportId").value(J7ImportTestArtifact.EXPORT_ID))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(importedBody.length).isLessThanOrEqualTo(J7ImportHttpContract.MAXIMUM_ACK_BYTES);
        assertThat(StrictJson.keys(importedBody)).containsExactlyInAnyOrder(
                "protocolVersion",
                "remoteImportId",
                "status",
                "exportId",
                "fileSha256",
                "dataSha256",
                "receivedAt");

        service.duplicate = true;
        mvc.perform(validRequest(artifact))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));
    }

    @Test
    void acknowledgementEncodingIsIndependentFromTheGlobalJacksonConfiguration()
            throws Exception {
        byte[] body = mvcWithHostileGlobalMapper(new StubService())
                .perform(validRequest(J7ImportTestArtifact.valid()))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(J7ImportHttpContract.ACK_MEDIA_TYPE))
                .andExpect(jsonPath("$.protocolVersion").value("1.0"))
                .andExpect(jsonPath("$.remoteImportId")
                        .value("cccccccc-cccc-4ccc-8ccc-cccccccccccc"))
                .andExpect(jsonPath("$.receivedAt").value(RECEIVED_AT.toString()))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(StrictJson.keys(body)).containsExactlyInAnyOrder(
                "protocolVersion",
                "remoteImportId",
                "status",
                "exportId",
                "fileSha256",
                "dataSha256",
                "receivedAt");
        assertThat(new String(body, java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("protocol_version", "remote_import_id", "received_at");
    }

    @Test
    void mapsDurableConflictTo409WithoutPositiveAcknowledgement() throws Exception {
        StubService service = new StubService();
        service.conflict = true;

        String response = mvc(service).perform(validRequest(J7ImportTestArtifact.valid()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("J7_IMPORT_CONFLICT"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("remoteImportId", "IMPORTED", "DUPLICATE");
    }

    @Test
    void mapsAnIncompatibleAcceptHeaderToTheContractual400() throws Exception {
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        MockHttpServletRequestBuilder request = protocolRequest(
                artifact,
                java.util.Map.of(J7ImportHttpContract.ACCEPT, "application/json"))
                .requestAttr(
                        J7ClientCertificateFilter.CLIENT_CERTIFICATE_SHA256_ATTRIBUTE,
                        CERTIFICATE_SHA256);

        mvc(new StubService()).perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_ACCEPT"));
    }

    @Test
    void mapsAnInternalPersistenceFailureWithoutAPositiveAcknowledgement() throws Exception {
        StubService service = new StubService();
        service.failure = true;

        String response = mvc(service).perform(validRequest(J7ImportTestArtifact.valid()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("J7_IMPORT_INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("remoteImportId", "IMPORTED", "DUPLICATE");
    }

    @Test
    void refusesInvalidHashesAndMissingAuthenticatedIdentityWithoutEchoingInputs()
            throws Exception {
        StubService service = new StubService();
        J7ImportTestArtifact.Artifact artifact = J7ImportTestArtifact.valid();
        String invalidFileHash = "0".repeat(64);
        MockHttpServletRequestBuilder invalidHash = protocolRequest(
                artifact,
                java.util.Map.of(
                        J7ImportHttpContract.FILE_SHA256,
                        invalidFileHash,
                        J7ImportHttpContract.IDEMPOTENCY_KEY,
                        "j7:" + J7ImportTestArtifact.EXPORT_ID
                                + ":sha256:" + invalidFileHash)).requestAttr(
                        J7ClientCertificateFilter.CLIENT_CERTIFICATE_SHA256_ATTRIBUTE,
                        CERTIFICATE_SHA256);

        String invalidResponse = mvc(service).perform(invalidHash)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_HASH"))
                .andReturn().getResponse().getContentAsString();
        assertThat(invalidResponse).doesNotContain("0".repeat(64), J7ImportTestArtifact.EXPORT_ID);

        MockHttpServletRequestBuilder missingIdentity = protocolRequest(artifact);
        mvc(service).perform(missingIdentity)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("J7_CLIENT_IDENTITY_REQUIRED"));
    }

    private MockMvc mvc(StubService service) {
        return MockMvcBuilders.standaloneSetup(
                        new J7ImportController(new StrictJ7ImportParser(), service))
                .setControllerAdvice(new J7ImportProblemHandler())
                .build();
    }

    private MockMvc mvcWithHostileGlobalMapper(StubService service) {
        tools.jackson.databind.json.JsonMapper hostileGlobalMapper =
                tools.jackson.databind.json.JsonMapper.builder()
                        .propertyNamingStrategy(
                                tools.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                        .build();
        return MockMvcBuilders.standaloneSetup(
                        new J7ImportController(new StrictJ7ImportParser(), service))
                .setControllerAdvice(new J7ImportProblemHandler())
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new JacksonJsonHttpMessageConverter(hostileGlobalMapper))
                .build();
    }

    private MockHttpServletRequestBuilder validRequest(J7ImportTestArtifact.Artifact artifact) {
        return protocolRequest(artifact).requestAttr(
                J7ClientCertificateFilter.CLIENT_CERTIFICATE_SHA256_ATTRIBUTE,
                CERTIFICATE_SHA256);
    }

    private MockHttpServletRequestBuilder protocolRequest(J7ImportTestArtifact.Artifact artifact) {
        return protocolRequest(artifact, java.util.Map.of());
    }

    private MockHttpServletRequestBuilder protocolRequest(
            J7ImportTestArtifact.Artifact artifact,
            java.util.Map<String, String> overrides) {
        MockHttpServletRequestBuilder request = post(J7ImportHttpContract.PATH)
                .content(artifact.body());
        artifact.headers().forEach((name, values) ->
                values.forEach(value -> request.header(
                        name,
                        overrides.getOrDefault(name, value))));
        return request;
    }

    private static final class StubService extends J7ImportService {

        private boolean duplicate;
        private boolean conflict;
        private boolean failure;

        private StubService() {
            super(
                    new UnsupportedStore(),
                    new J7ImportRetentionPolicy(Duration.ofDays(30)),
                    Clock.fixed(RECEIVED_AT, ZoneOffset.UTC));
        }

        @Override
        public J7ImportResult receive(J7ImportCommand command, String certificateSha256) {
            if (failure) {
                throw new IllegalStateException("synthetic persistence failure");
            }
            if (conflict) {
                return new J7ImportResult.Conflict(J7ImportAuditReason.EXPORT_ID_DIVERGENCE);
            }
            StoredJ7Import receipt = new StoredJ7Import(
                    UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
                    command.idempotencyKey(),
                    command.exportId(),
                    command.canonicalEventId(),
                    command.providerEventId(),
                    StoredJ7Import.PROTOCOL_VERSION,
                    command.schemaId(),
                    command.schemaVersion(),
                    command.generatedAt(),
                    command.generatorVersion(),
                    StoredJ7Import.SELECTION_MODE,
                    command.sourceSetSha256(),
                    command.validationStatus(),
                    command.validationDecidedAt(),
                    command.fileSha256(),
                    command.dataSha256(),
                    certificateSha256,
                    command.content().length,
                    RECEIVED_AT,
                    RECEIVED_AT.plus(Duration.ofDays(30)),
                    Optional.empty());
            return duplicate
                    ? new J7ImportResult.Duplicate(receipt)
                    : new J7ImportResult.Imported(receipt);
        }
    }

    private static final class UnsupportedStore implements J7ImportStore {

        @Override
        public void acquireImportLock(UUID exportId, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredJ7Import> findByIdempotencyKey(String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StoredJ7Import> findByExportId(UUID exportId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertImport(StoredJ7Import storedImport, byte[] payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<byte[]> findPayload(UUID importId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendAudit(J7ImportAuditEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendAcceptedOutbox(J7AcceptedOutboxEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int purgeExpiredPayloads(
                Instant expiredBefore,
                Instant purgedAt,
                int maximumRows) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StrictJson {

        private StrictJson() {
        }

        static java.util.Set<String> keys(byte[] bytes) {
            try {
                tools.jackson.databind.JsonNode node = tools.jackson.databind.json.JsonMapper
                        .builder().build().readTree(bytes);
                java.util.Set<String> names = new java.util.LinkedHashSet<>();
                names.addAll(node.propertyNames());
                return names;
            }
            catch (tools.jackson.core.JacksonException exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
