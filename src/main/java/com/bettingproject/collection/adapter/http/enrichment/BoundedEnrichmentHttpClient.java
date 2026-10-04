package com.bettingproject.collection.adapter.http.enrichment;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.bettingproject.collection.application.enrichment.EnrichmentProviderClient;
import com.bettingproject.collection.application.enrichment.EnrichmentProviderRequest;
import com.bettingproject.collection.application.enrichment.EnrichmentProviderResponse;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** One bounded, non-retrying HTTP operation after a committed budget permit. */
abstract class BoundedEnrichmentHttpClient implements EnrichmentProviderClient, AutoCloseable {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;

    private final HttpClient client;
    private final String credential;
    private final Clock clock;
    private final Duration timeout;
    private final int maximumBytes;

    BoundedEnrichmentHttpClient(HttpClient client, String credential, Clock clock,
            Duration timeout, int maximumBytes) {
        this.client = client;
        this.credential = credential;
        this.clock = Objects.requireNonNull(clock);
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero() || timeout.isNegative() || maximumBytes < 1) {
            throw new IllegalArgumentException("invalid transport bounds");
        }
        this.maximumBytes = maximumBytes;
    }

    static HttpClient productionTransport() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    static boolean usableCredential(String value) {
        return value != null && !value.isEmpty() && value.length() <= 4096 && value.equals(value.strip())
                && value.chars().allMatch(character -> character >= 33 && character <= 126);
    }

    static URI validatedBaseUri(URI value, String officialHost) {
        Objects.requireNonNull(value, "baseUri");
        boolean official = "https".equalsIgnoreCase(value.getScheme())
                && officialHost.equalsIgnoreCase(value.getHost())
                && (value.getPort() == -1 || value.getPort() == 443);
        boolean loopbackTest = "http".equalsIgnoreCase(value.getScheme())
                && ("127.0.0.1".equals(value.getHost()) || "localhost".equalsIgnoreCase(value.getHost()))
                && value.getPort() >= 1 && value.getPort() <= 65535;
        if ((!official && !loopbackTest) || value.getUserInfo() != null || value.getQuery() != null
                || value.getFragment() != null || value.getPath() == null || !value.getPath().endsWith("/")) {
            throw new IllegalArgumentException("invalid enrichment transport base");
        }
        return value;
    }

    @Override public final boolean available() { return client != null && usableCredential(credential); }

    @Override
    public final EnrichmentProviderResponse fetch(EnrichmentProviderRequest request) {
        Objects.requireNonNull(request);
        if (!provider().equals(request.provider())) {
            throw new IllegalArgumentException("Mismatched enrichment provider");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Enrichment HTTP cannot run inside a transaction");
        }
        Instant requestedAt = now();
        if (!available()) { return failure(requestedAt, "PROVIDER_DISABLED"); }
        URI uri = requestUri(request);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Accept", "application/json").GET();
        authenticate(builder, credential);
        try {
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] payload = body.readNBytes(maximumBytes + 1);
                Instant completedAt = notBefore(now(), requestedAt);
                Long remaining = quota(response);
                if (payload.length > maximumBytes) {
                    return new EnrichmentProviderResponse(requestedAt, completedAt, response.statusCode(),
                            new byte[0], remaining, "RESPONSE_TOO_LARGE");
                }
                if (echoesCredential(payload)) {
                    return new EnrichmentProviderResponse(requestedAt, completedAt, response.statusCode(),
                            new byte[0], remaining, "SECRET_ECHO");
                }
                return new EnrichmentProviderResponse(requestedAt, completedAt, response.statusCode(),
                        payload, remaining, null);
            }
        }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return failure(requestedAt, "UNCERTAIN_RESPONSE");
        }
        catch (IOException | RuntimeException failure) {
            // Do not expose exception text, URI, credential, or raw server response.
            return failure(requestedAt, "UNCERTAIN_RESPONSE");
        }
    }

    abstract URI requestUri(EnrichmentProviderRequest request);
    abstract void authenticate(HttpRequest.Builder builder, String credential);
    Long quota(HttpResponse<?> response) { return null; }

    @Override public final void close() { if (client != null) { client.close(); } }

    private EnrichmentProviderResponse failure(Instant requestedAt, String code) {
        return new EnrichmentProviderResponse(requestedAt, notBefore(now(), requestedAt), null,
                new byte[0], null, code);
    }

    private boolean echoesCredential(byte[] bytes) {
        String body = new String(bytes, StandardCharsets.UTF_8);
        String escaped = credential.replace("\\", "\\\\").replace("\"", "\\\"");
        String encoded = URLEncoder.encode(credential, StandardCharsets.UTF_8).replace("+", "%20");
        return body.contains(credential) || body.contains(escaped) || body.contains(encoded);
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static Instant notBefore(Instant value, Instant requested) { return value.isBefore(requested) ? requested : value; }
}
