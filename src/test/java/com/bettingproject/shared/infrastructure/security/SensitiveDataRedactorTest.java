package com.bettingproject.shared.infrastructure.security;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataRedactorTest {

    private final SensitiveDataRedactor redactor = new SensitiveDataRedactor();

    @Test
    void sensitiveHeadersAreMasked() {
        Map<String, String> redacted = redactor.redactHeaders(Map.of(
                "Authorization", "Bearer test-only-sensitive-value",
                "X-Request-Id", "request-42",
                "Cookie", "session=test-only-cookie"));

        assertThat(redacted)
                .containsEntry("Authorization", SensitiveDataRedactor.REDACTED)
                .containsEntry("Cookie", SensitiveDataRedactor.REDACTED)
                .containsEntry("X-Request-Id", "request-42");
    }

    @Test
    void sensitiveQueryParametersAreMasked() {
        String redacted = redactor.redactUrl(
                "https://provider.invalid/fixtures?league=42&api_key=fixture&token=sample");

        assertThat(redacted)
                .doesNotContain("api_key=fixture", "token=sample")
                .contains("api_key=[REDACTED]", "token=[REDACTED]");
    }
}
