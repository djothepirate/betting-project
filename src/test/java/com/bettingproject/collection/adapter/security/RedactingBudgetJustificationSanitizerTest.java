package com.bettingproject.collection.adapter.security;

import com.bettingproject.shared.infrastructure.security.SensitiveDataRedactor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RedactingBudgetJustificationSanitizerTest {
    private final RedactingBudgetJustificationSanitizer sanitizer =
            new RedactingBudgetJustificationSanitizer(new SensitiveDataRedactor());

    @Test
    void redactsAuthorizationBearerAsAWholeNotJustTheBearerPrefix() {
        String raw = "Proof checked; Authorization: Bearer fake-bearer-value; api_key=fake-query-value; "
                + "https://provider.invalid/path?token=fake-url-value";
        assertThat(sanitizer.sanitize(raw)).contains("Proof checked", "[REDACTED]")
                .doesNotContain("fake-bearer-value", "fake-query-value", "fake-url-value");
    }

    @Test
    void redactsStandaloneCredentialsAndHeaderNamesCaseInsensitively() {
        String raw = "Bearer fake-direct-value; X-RAPIDAPI-KEY: fake-rapid-value; "
                + "client_secret=fake-client-value; ACCESS_TOKEN=fake-access-value";
        assertThat(sanitizer.sanitize(raw)).contains("[REDACTED]")
                .doesNotContain("fake-direct-value", "fake-rapid-value", "fake-client-value", "fake-access-value");
    }

    @Test
    void redactsQuotedNamedCredentialsIncludingSpacesAndEscapedQuotes() {
        String raw = "api_key=\"fake quoted value\"; client_secret='fake single value'; "
                + "access_token=\"fake \\\"escaped\\\" value\"; proof checked";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("api_key=[REDACTED]; client_secret=[REDACTED]; "
                + "access_token=[REDACTED]; proof checked");
        assertThat(sanitizer.sanitize("token=\"fake unterminated value"))
                .isEqualTo("token=[REDACTED]");
        assertThat(sanitizer.sanitize("https://provider.invalid/path?key=\"fake spaced value\"&page=2"))
                .isEqualTo("https://provider.invalid/path?key=[REDACTED]&page=2");
    }

    @Test
    void removesQuotedBearerAndBasicAuthorizationCredentials() {
        assertThat(sanitizer.sanitize("Bearer \"fake spaced value\"; Authorization: Basic fake-basic-value"))
                .doesNotContain("fake", "spaced", "value").contains("[REDACTED]");
    }

    @Test
    void redactsTheEntirePrivateKeyBlock() {
        String begin = "-----BEGIN " + "PRIVATE KEY-----";
        String end = "-----END " + "PRIVATE KEY-----";
        String raw = "Proof checked " + begin + "\nTEST-ONLY-PLACEHOLDER\n" + end + " manually";
        assertThat(sanitizer.sanitize(raw)).isEqualTo("Proof checked [REDACTED] manually");
    }

    @Test
    void redactsKnownTokenFormatsWithoutAHeaderName() {
        String github = "ghp_" + "s".repeat(36);
        String githubFineGrained = "github_pat_" + "s".repeat(80);
        String aws = "AKIA" + "S".repeat(16);
        assertThat(sanitizer.sanitize("Credentials " + github + "; " + githubFineGrained + "; " + aws))
                .isEqualTo("Credentials [REDACTED]; [REDACTED]; [REDACTED]");
    }

    @Test
    void preservesProofHashesAndOrdinaryContentWhileRemovingControls() {
        String hash = "a".repeat(64);
        assertThat(sanitizer.sanitize("  Proof\tsha256=" + hash + "\nverified\u200Bmanually  "))
                .isEqualTo("Proof sha256=" + hash + " verified manually");
        assertThat(sanitizer.sanitize("proof-id: synthetic-calendar/2026-09-13"))
                .isEqualTo("proof-id: synthetic-calendar/2026-09-13");
    }

    @Test
    void rejectsNullWithoutEchoingAnySubmittedValue() {
        assertThatNullPointerException().isThrownBy(() -> sanitizer.sanitize(null));
    }
}
