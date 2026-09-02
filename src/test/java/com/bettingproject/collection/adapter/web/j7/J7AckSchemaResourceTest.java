package com.bettingproject.collection.adapter.web.j7;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class J7AckSchemaResourceTest {

    @Test
    void acknowledgementSchemaRemainsByteIdenticalToTheVersionedSenderContract()
            throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/schemas/j7-delivery-ack-v1.schema.json")) {
            assertThat(input).isNotNull();
            assertThat(J7ImportTestArtifact.sha256(input.readAllBytes()))
                    .isEqualTo("8a9940a0622c3694493eb64ed24d9b7d3b10c581d9eefe89415f7194b24a0a62");
        }
    }
}
