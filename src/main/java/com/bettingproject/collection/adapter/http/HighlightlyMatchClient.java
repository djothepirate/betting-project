package com.bettingproject.collection.adapter.http;

import com.bettingproject.collection.application.EvidenceCollectionRequest;
import com.bettingproject.collection.application.ProviderCallResult;
import com.bettingproject.collection.application.ProviderMatchClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class HighlightlyMatchClient implements ProviderMatchClient {

    public static final String PROVIDER = "Highlightly";
    public static final String CONNECTOR_VERSION = "highlightly-enrichment-v1";
    public static final URI DEFAULT_BASE_URI = URI.create("https://soccer.highlightly.net/");

    private final HttpClient httpClient;
    private final URI baseUri;
    private final String apiKey;

    public HighlightlyMatchClient(HttpClient httpClient, URI baseUri, String apiKey) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient");
        this.baseUri = requireHttpsBaseUri(baseUri);
        this.apiKey = requireSecret(apiKey);
    }

    public static HighlightlyMatchClient production(String apiKey) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new HighlightlyMatchClient(client, DEFAULT_BASE_URI, apiKey);
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ProviderCallResult fetch(EvidenceCollectionRequest request) throws IOException, InterruptedException {
        String endpoint = request.endpoint().relativePath(request.providerMatchId());
        URI uri = baseUri.resolve(endpoint.substring(1));
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("x-rapidapi-key", apiKey)
                .header("x-rapidapi-host", DEFAULT_BASE_URI.getHost())
                .GET()
                .build();

        Instant startedAt = Instant.now();
        HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        Instant completedAt = Instant.now();
        byte[] payload = response.body();
        rejectEchoedSecret(payload);

        return new ProviderCallResult(
                PROVIDER,
                endpoint,
                startedAt,
                completedAt,
                response.statusCode(),
                safeHeaders(response),
                payload,
                CONNECTOR_VERSION);
    }

    private Map<String, String> safeHeaders(HttpResponse<?> response) {
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (isSafeResponseHeader(normalized) && !values.isEmpty()) {
                headers.put(normalized, values.getFirst());
            }
        });
        return Map.copyOf(headers);
    }

    private static boolean isSafeResponseHeader(String name) {
        return name.equals("content-type")
                || name.equals("date")
                || name.equals("retry-after")
                || name.startsWith("x-ratelimit-")
                || name.startsWith("x-requests-")
                || name.startsWith("x-requestcounter-");
    }

    private void rejectEchoedSecret(byte[] payload) {
        String body = new String(payload, StandardCharsets.UTF_8);
        if (body.contains(apiKey)) {
            throw new SecurityException("Provider response unexpectedly contains the authentication secret");
        }
    }

    private static URI requireHttpsBaseUri(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null) {
            throw new IllegalArgumentException("baseUri must be an HTTPS URI with a host");
        }
        return value;
    }

    private static String requireSecret(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Highlightly API key is missing");
        }
        return value;
    }
}
