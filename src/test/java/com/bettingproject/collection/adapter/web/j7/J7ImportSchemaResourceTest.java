package com.bettingproject.collection.adapter.web.j7;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class J7ImportSchemaResourceTest {

    @Test
    void keepsTheAuditedJ7SchemaByteIdentical() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                J7ImportHttpContract.SCHEMA_RESOURCE)) {
            assertThat(input).isNotNull();
            assertThat(sha256(input.readAllBytes()))
                    .isEqualTo("45c3350f4f05acf2bdf4cd9806a965723e0a73fec38f2c9347b1f9f85590c938");
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
