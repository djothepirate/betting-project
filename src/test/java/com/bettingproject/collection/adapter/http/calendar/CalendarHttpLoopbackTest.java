package com.bettingproject.collection.adapter.http.calendar;

import com.bettingproject.collection.application.calendar.CalendarPageRequest;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Real JDK transport against an ephemeral IPv4 loopback only, never a provider endpoint. */
class CalendarHttpLoopbackTest {
    private static final String CREDENTIAL = "TEST_ONLY_PLACEHOLDER";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    void highlightlyReceivesExactBytesAndQuotaThroughRealJdkSubscriber() throws Exception {
        byte[] body = "{\"data\":[],\"synthetic\":true}".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> authentication = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicInteger requests = new AtomicInteger();
        try (Loopback server = new Loopback(exchange -> {
            requests.incrementAndGet();
            authentication.set(exchange.getRequestHeaders().getFirst("x-rapidapi-key"));
            query.set(exchange.getRequestURI().getRawQuery());
            exchange.getResponseHeaders().add("x-ratelimit-requests-remaining", "73");
            reply(exchange, 200, body);
        }); var client = new HighlightlyCalendarPageClient(BoundedCalendarHttpClient.productionTransport(),
                CREDENTIAL, Clock.systemUTC(), TIMEOUT, 1024, server.uri())) {
            var response = client.fetch(request("highlightly", "920001"));
            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(response.failureCode()).isNull();
            assertThat(response.body()).isEqualTo(body);
            assertThat(response.quotaRemaining()).isEqualTo(73L);
            assertThat(response.receivedAt()).isAfterOrEqualTo(response.requestedAt());
            assertThat(authentication.get()).isEqualTo(CREDENTIAL);
            assertThat(query.get()).isEqualTo(
                    "leagueId=920001&season=2030&date=2030-08-10&timezone=Etc%2FUTC&limit=100&offset=0");
            assertThat(requests).hasValue(1);
        }
    }

    @Test
    void footballDataUsesItsOwnAuthenticationAndDoesNotAcquireHighlightlyQuota() throws Exception {
        AtomicReference<String> authentication = new AtomicReference<>();
        AtomicReference<String> target = new AtomicReference<>();
        try (Loopback server = new Loopback(exchange -> {
            authentication.set(exchange.getRequestHeaders().getFirst("X-Auth-Token"));
            target.set(exchange.getRequestURI().toString());
            exchange.getResponseHeaders().add("x-ratelimit-requests-remaining", "73");
            reply(exchange, 200, "{\"matches\":[]}".getBytes(StandardCharsets.UTF_8));
        }); var client = new FootballDataCalendarPageClient(BoundedCalendarHttpClient.productionTransport(),
                CREDENTIAL, Clock.systemUTC(), TIMEOUT, 1024, server.uri())) {
            var response = client.fetch(request("football-data.org", "SYN"));
            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(response.failureCode()).isNull();
            assertThat(response.quotaRemaining()).isNull();
            assertThat(authentication.get()).isEqualTo(CREDENTIAL);
            assertThat(target.get()).isEqualTo(
                    "/competitions/SYN/matches?season=2030&dateFrom=2030-08-10&dateTo=2030-08-11");
        }
    }

    @Test
    void redirectIsReturnedWithoutCallingSecondLocalUrl() throws Exception {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        try (Loopback server = new Loopback(exchange -> {
            if (exchange.getRequestURI().getPath().equals("/second")) {
                second.incrementAndGet();
                reply(exchange, 200, "{}".getBytes(StandardCharsets.UTF_8));
            } else {
                first.incrementAndGet();
                exchange.getResponseHeaders().add("Location", "/second");
                reply(exchange, 302, "{}".getBytes(StandardCharsets.UTF_8));
            }
        }); var client = new HighlightlyCalendarPageClient(BoundedCalendarHttpClient.productionTransport(),
                CREDENTIAL, Clock.systemUTC(), TIMEOUT, 1024, server.uri())) {
            var response = client.fetch(request("highlightly", "920001"));
            assertThat(response.httpStatus()).isEqualTo(302);
            assertThat(response.failureCode()).isNull();
            assertThat(first).hasValue(1);
            assertThat(second).hasValue(0);
        }
    }

    @Test
    void realJdkBodySubscriberCancelsOversizedBodyAndReturnsOnlyBoundedPrefix() throws Exception {
        byte[] body = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        try (Loopback server = new Loopback(exchange -> {
            requests.incrementAndGet();
            reply(exchange, 200, body);
        }); var client = new HighlightlyCalendarPageClient(BoundedCalendarHttpClient.productionTransport(),
                CREDENTIAL, Clock.systemUTC(), TIMEOUT, 16, server.uri())) {
            var response = client.fetch(request("highlightly", "920001"));
            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(response.failureCode()).isEqualTo("RESPONSE_TOO_LARGE");
            assertThat(response.body()).isEqualTo("ABCDEFGHIJKLMNOP".getBytes(StandardCharsets.UTF_8));
            assertThat(requests).hasValue(1);
        }
    }

    private static CalendarPageRequest request(String provider, String competition) {
        return new CalendarPageRequest(new ProviderCapabilityKey(provider, competition, "2030", "REGULAR_SEASON",
                CapabilityDataType.CALENDAR), LocalDate.of(2030, 8, 10), 2030, 0, 100);
    }

    private static void reply(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
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
