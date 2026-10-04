package com.bettingproject.collection.adapter.http.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.bettingproject.collection.application.enrichment.EnrichmentProviderRequest;
import com.bettingproject.enrichment.domain.EnrichmentFamily;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class EnrichmentHttpLoopbackTest {
    private static final String CREDENTIAL = "TEST_ONLY_PLACEHOLDER";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    void highlightlyUsesOnlyTheExactLocalPathAndProviderAuthentication() throws Exception {
        byte[] payload = "{\"synthetic\":true}".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> host = new AtomicReference<>();
        try (Loopback server = new Loopback(exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            key.set(exchange.getRequestHeaders().getFirst("x-rapidapi-key"));
            host.set(exchange.getRequestHeaders().getFirst("x-rapidapi-host"));
            exchange.getResponseHeaders().add("x-ratelimit-requests-remaining", "13");
            reply(exchange, 200, payload);
        }); var client = new HighlightlyEnrichmentClient(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build(), CREDENTIAL, Clock.systemUTC(), TIMEOUT,
                1024, server.uri())) {
            var response = client.fetch(request("highlightly", "34567", EnrichmentFamily.MATCH_DETAIL));
            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(payload);
            assertThat(response.quotaRemaining()).isEqualTo(13L);
            assertThat(path.get()).isEqualTo("/matches/34567");
            assertThat(key.get()).isEqualTo(CREDENTIAL);
            assertThat(host.get()).isEqualTo("soccer.highlightly.net");
        }
    }

    @Test
    void footballDataUsesItsTokenAndDoesNotTreatHeadersAsAQuotaCounter() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        try (Loopback server = new Loopback(exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            token.set(exchange.getRequestHeaders().getFirst("X-Auth-Token"));
            exchange.getResponseHeaders().add("x-ratelimit-requests-remaining", "13");
            reply(exchange, 200, "{}".getBytes(StandardCharsets.UTF_8));
        }); var client = new FootballDataEnrichmentClient(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build(), CREDENTIAL, Clock.systemUTC(), TIMEOUT,
                1024, server.uri())) {
            var response = client.fetch(request("football-data.org", "34567", EnrichmentFamily.MATCH_DETAIL));
            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(response.quotaRemaining()).isNull();
            assertThat(path.get()).isEqualTo("/matches/34567");
            assertThat(token.get()).isEqualTo(CREDENTIAL);
            assertThat(client.supports(EnrichmentFamily.LINEUP)).isFalse();
        }
    }

    @Test
    void redirectIsNotFollowedAndOversizeOrSecretEchoBodiesAreNotRetained() throws Exception {
        AtomicInteger redirected = new AtomicInteger();
        try (Loopback server = new Loopback(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/second")) {
                redirected.incrementAndGet();
                reply(exchange, 200, "unexpected".getBytes(StandardCharsets.UTF_8));
            } else {
                exchange.getResponseHeaders().add("Location", "/second");
                reply(exchange, 302, "{}".getBytes(StandardCharsets.UTF_8));
            }
        }); var client = new HighlightlyEnrichmentClient(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build(), CREDENTIAL, Clock.systemUTC(), TIMEOUT,
                4, server.uri())) {
            var redirect = client.fetch(request("highlightly", "34567", EnrichmentFamily.MATCH_DETAIL));
            assertThat(redirect.httpStatus()).isEqualTo(302);
            assertThat(redirect.failureCode()).isNull();
            assertThat(redirected).hasValue(0);
        }

        try (Loopback server = new Loopback(exchange -> reply(exchange, 200,
                "123456789".getBytes(StandardCharsets.UTF_8)));
             var client = new HighlightlyEnrichmentClient(HttpClient.newBuilder().build(), CREDENTIAL,
                     Clock.systemUTC(), TIMEOUT, 4, server.uri())) {
            var response = client.fetch(request("highlightly", "34567", EnrichmentFamily.MATCH_DETAIL));
            assertThat(response.failureCode()).isEqualTo("RESPONSE_TOO_LARGE");
            assertThat(response.body()).isEmpty();
        }

        try (Loopback server = new Loopback(exchange -> reply(exchange, 200,
                ("echo " + CREDENTIAL).getBytes(StandardCharsets.UTF_8)));
             var client = new HighlightlyEnrichmentClient(HttpClient.newBuilder().build(), CREDENTIAL,
                     Clock.systemUTC(), TIMEOUT, 1024, server.uri())) {
            var response = client.fetch(request("highlightly", "34567", EnrichmentFamily.MATCH_DETAIL));
            assertThat(response.failureCode()).isEqualTo("SECRET_ECHO");
            assertThat(response.body()).isEmpty();
        }
    }

    @Test
    void cannotConfigureAnArbitraryOrCredentialBearingBaseUri() {
        assertThatThrownBy(() -> new HighlightlyEnrichmentClient(HttpClient.newHttpClient(), CREDENTIAL,
                Clock.systemUTC(), TIMEOUT, 1024, URI.create("http://example.invalid/")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FootballDataEnrichmentClient(HttpClient.newHttpClient(), CREDENTIAL,
                Clock.systemUTC(), TIMEOUT, 1024, URI.create("https://token@api.football-data.org/v4/")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EnrichmentProviderRequest request(String provider, String fixture, EnrichmentFamily family) {
        String endpoint = switch (family) {
            case MATCH_DETAIL -> "enrichment/matches";
            case LINEUP -> "enrichment/lineups";
            case TEAM_STATS -> "enrichment/statistics";
            case EVENTS -> "enrichment/events";
            case PLAYER_STATS -> "enrichment/box-score";
        };
        return new EnrichmentProviderRequest(UUID.randomUUID(), provider, fixture, family, endpoint, "synthetic-v1");
    }

    private static void reply(HttpExchange exchange, int status, byte[] payload) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            if (payload.length > 0) {
                exchange.getResponseBody().write(payload);
            }
        }
    }

    private static final class Loopback implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        private Loopback(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", handler);
            server.start();
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        @Override
        public void close() {
            server.stop(0);
            executor.close();
        }
    }
}
