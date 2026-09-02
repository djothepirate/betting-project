package com.bettingproject.catalog.adapter.security;

import com.bettingproject.shared.infrastructure.security.SensitiveDataRedactor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactingDecisionJustificationSanitizerTest {

    private final RedactingDecisionJustificationSanitizer sanitizer =
            new RedactingDecisionJustificationSanitizer(new SensitiveDataRedactor());

    @Test
    void redactsCommonCredentialsAndRemovesControlCharacters() {
        String raw = "Provider review\nAuthorization: Bearer fake-bearer-value; "
                + "api_key=fake-query-value; "
                + "https://provider.invalid/path?token=fake-url-value";

        String sanitized = sanitizer.sanitize(raw);

        assertThat(sanitized)
                .contains("Provider review", "[REDACTED]")
                .doesNotContain("fake-bearer-value", "fake-query-value", "fake-url-value", "\n");
    }

    @Test
    void redactsPrivateKeyBlocksAsAWhole() {
        String privateKeyBegin = "-----BEGIN " + "PRIVATE KEY-----";
        String privateKeyEnd = "-----END " + "PRIVATE KEY-----";
        String raw = "Reviewed " + privateKeyBegin + "\n"
                + "TEST-ONLY-PLACEHOLDER\n"
                + privateKeyEnd + " manually";

        assertThat(sanitizer.sanitize(raw))
                .isEqualTo("Reviewed [REDACTED] manually");
    }
}
