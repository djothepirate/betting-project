package com.bettingproject.collection.adapter.http.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EnrichmentHttpRuntimeSafetyTest {
    private static final List<String> SAFE_ARGUMENTS = List.of(
            "-Djdk.httpclient.disableRetryConnect=true", "-Djdk.httpclient.redirects.retrylimit=1");
    private static final Map<String, String> SAFE_PROPERTIES = Map.of(
            EnrichmentHttpRuntimeSafety.DISABLE_RETRY, "true",
            EnrichmentHttpRuntimeSafety.REDIRECT_LIMIT, "1");

    @Test
    void requiresSafeProcessStartupAndCurrentHttpClientOptions() {
        assertThat(EnrichmentHttpRuntimeSafety.allowedStartup(SAFE_ARGUMENTS, SAFE_PROPERTIES::get)).isTrue();
        assertThat(EnrichmentHttpRuntimeSafety.allowedStartup(List.of(), SAFE_PROPERTIES::get)).isFalse();
        assertThat(EnrichmentHttpRuntimeSafety.allowedStartup(SAFE_ARGUMENTS, key -> null)).isFalse();
        assertThat(EnrichmentHttpRuntimeSafety.allowedStartup(SAFE_ARGUMENTS,
                key -> key.equals(EnrichmentHttpRuntimeSafety.REDIRECT_LIMIT)
                        ? "5" : SAFE_PROPERTIES.get(key))).isFalse();
    }

    @Test
    void refusesHttpLoggingModesThatCouldExposeProviderCredentials() {
        for (String value : List.of("headers", "requests,headers", "content", "all")) {
            assertThat(EnrichmentHttpRuntimeSafety.allowedStartup(SAFE_ARGUMENTS,
                    key -> key.equals(EnrichmentHttpRuntimeSafety.HTTP_LOG) ? value : SAFE_PROPERTIES.get(key)))
                    .isFalse();
            var unsafe = List.of(SAFE_ARGUMENTS.getFirst(), SAFE_ARGUMENTS.getLast(),
                    "-D" + EnrichmentHttpRuntimeSafety.HTTP_LOG + "=" + value);
            assertThat(EnrichmentHttpRuntimeSafety.allowedStartup(unsafe, SAFE_PROPERTIES::get)).isFalse();
        }
    }
}
