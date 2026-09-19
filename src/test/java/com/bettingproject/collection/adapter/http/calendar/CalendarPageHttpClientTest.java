package com.bettingproject.collection.adapter.http.calendar;

import com.bettingproject.collection.application.calendar.CalendarPageClient;
import com.bettingproject.collection.application.calendar.CalendarPageRequest;
import com.bettingproject.collection.application.calendar.CalendarPageResponse;
import com.bettingproject.collection.domain.capability.CapabilityDataType;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarPageHttpClientTest {
    private static final Instant NOW = Instant.parse("2026-09-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CREDENTIAL = "synthetic-calendar-credential";

    @Test
    void highlightlyUsesExactLiteralReferenceEncodedQueryAndSafeCounter() {
        FakeHttpClient transport = new FakeHttpClient("{}".getBytes(StandardCharsets.UTF_8));
        transport.headers = Map.of("x-ratelimit-requests-remaining", List.of("87"),
                "Authorization", List.of(CREDENTIAL));
        CalendarPageResponse response = highlightly(transport).fetch(request("highlightly", "id?*%/ space", 40));

        assertThat(transport.request.uri().toString()).isEqualTo(
                "https://soccer.highlightly.net/matches?leagueId=id%3F*%25%2F%20space"
                        + "&season=2026&date=2026-09-18&timezone=Etc%2FUTC&limit=20&offset=40");
        assertThat(transport.request.headers().firstValue("x-rapidapi-key")).contains(CREDENTIAL);
        assertThat(transport.request.headers().firstValue("x-rapidapi-host")).contains("soccer.highlightly.net");
        assertThat(transport.request.headers().firstValue("X-Auth-Token")).isEmpty();
        assertThat(transport.request.method()).isEqualTo("GET");
        assertThat(transport.request.timeout()).contains(Duration.ofSeconds(30));
        assertThat(response.quotaRemaining()).isEqualTo(87);
        assertThat(response.failureCode()).isNull();
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.requestedAt()).isEqualTo(NOW);
        assertThat(response.receivedAt()).isEqualTo(NOW);
        assertThat(transport.sends).isEqualTo(1);
    }

    @Test
    void footballDataUsesExclusiveDateToWithoutInventingPeriodicQuota() {
        FakeHttpClient transport = new FakeHttpClient(new byte[0]);
        transport.headers = Map.of("x-ratelimit-requests-remaining", List.of("50"));
        var client = new FootballDataCalendarPageClient(transport, CREDENTIAL, CLOCK);
        CalendarPageResponse response = client.fetch(new CalendarPageRequest(
                key("football-data.org", "PD/ literal"), LocalDate.of(2026, 12, 31), 2026, 0, 20));

        assertThat(transport.request.uri().toString()).isEqualTo(
                "https://api.football-data.org/v4/competitions/PD%2F%20literal/matches"
                        + "?season=2026&dateFrom=2026-12-31&dateTo=2027-01-01");
        assertThat(transport.request.headers().firstValue("X-Auth-Token")).contains(CREDENTIAL);
        assertThat(transport.request.headers().firstValue("x-rapidapi-key")).isEmpty();
        assertThat(response.quotaRemaining()).isNull();
        assertThat(response.failureCode()).isNull();
        assertThatThrownBy(() -> client.fetch(request("football-data.org", "PD", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(transport.sends).isEqualTo(1);
    }

    @Test
    void defaultConfigurationDoesNotReadCredentialsOrActivateClients() {
        MockEnvironment environment = new MockEnvironment() {
            @Override
            public String getProperty(String key) {
                if (key.contains("api-key") || key.endsWith("API_KEY")) {
                    throw new AssertionError("Disabled connectors must not read credentials");
                }
                return super.getProperty(key);
            }
        };
        CalendarHttpConfiguration configuration = new CalendarHttpConfiguration();
        assertDisabled(configuration.highlightlyCalendarPageClient(environment, CLOCK));
        assertDisabled(configuration.footballDataCalendarPageClient(environment, CLOCK));
    }

    @Test
    void secretAloneOrEnablementWithoutSecretDoesNotActivateAnything() {
        CalendarHttpConfiguration configuration = new CalendarHttpConfiguration();
        MockEnvironment credentialOnly = new MockEnvironment().withProperty("HIGHLIGHTLY_API_KEY", CREDENTIAL);
        assertDisabled(configuration.highlightlyCalendarPageClient(credentialOnly, CLOCK));
        MockEnvironment enabledOnly = new MockEnvironment().withProperty("betting.providers.highlightly.enabled", "true");
        assertDisabled(configuration.highlightlyCalendarPageClient(enabledOnly, CLOCK));
        MockEnvironment malformed = new MockEnvironment().withProperty("betting.providers.highlightly.enabled", "true")
                .withProperty("HIGHLIGHTLY_API_KEY", "unsafe\nsynthetic");
        assertDisabled(configuration.highlightlyCalendarPageClient(malformed, CLOCK));
    }

    @ParameterizedTest
    @ValueSource(strings = {"control-api", "batch-worker", "replay"})
    void clientsAreConfinedToOperationalProfilesAndRemainInactive(String profile) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment());
            context.getEnvironment().setActiveProfiles(profile);
            context.registerBean(Clock.class, () -> CLOCK);
            context.register(CalendarHttpConfiguration.class);
            context.refresh();
            var clients = context.getBeansOfType(CalendarPageClient.class).values();
            assertThat(clients).hasSize(profile.equals("replay") ? 0 : 2);
            assertThat(clients).allSatisfy(CalendarPageHttpClientTest::assertDisabled);
        }
    }

    @Test
    void productionTransportHasFiveSecondConnectionTimeoutAndNoRedirects() {
        try (HttpClient client = BoundedCalendarHttpClient.productionTransport()) {
            assertThat(client.connectTimeout()).contains(Duration.ofSeconds(5));
            assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        }
    }

    @Test
    void responseAtLimitIsCompleteAndOversizedResponseKeepsOnlyBoundedPrefix() {
        FakeHttpClient exact = new FakeHttpClient(new byte[32]);
        var exactResponse = bounded(exact, Duration.ofSeconds(1), 32).fetch(request("highlightly", "fixture", 0));
        assertThat(exactResponse.body()).hasSize(32);
        assertThat(exactResponse.failureCode()).isNull();

        FakeHttpClient oversized = new FakeHttpClient(new byte[33]);
        var partial = bounded(oversized, Duration.ofSeconds(1), 32).fetch(request("highlightly", "fixture", 0));
        assertThat(partial.body()).hasSize(32);
        assertThat(partial.failureCode()).isEqualTo("RESPONSE_TOO_LARGE");
        assertThat(oversized.cancelled).isTrue();
        assertThat(oversized.sends).isEqualTo(1);
        assertThat(BoundedCalendarHttpClient.MAX_RESPONSE_BYTES).isEqualTo(5 * 1024 * 1024);
    }

    @Test
    void overallDeadlineAlsoBoundsBodyAfterHeadersAndKeepsSafePrefix() {
        FakeHttpClient transport = new FakeHttpClient("partial".getBytes(StandardCharsets.UTF_8));
        transport.neverComplete = true;
        long started = System.nanoTime();
        var response = bounded(transport, Duration.ofMillis(30), 100).fetch(request("highlightly", "fixture", 0));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("partial");
        assertThat(response.failureCode()).isEqualTo("UNCERTAIN_RESPONSE");
        assertThat(transport.cancelled).isTrue();
        assertThat(transport.sends).isEqualTo(1);
    }

    @Test
    void ioFailureAfterPartialBodyIsUncertainWithoutExposingException() {
        FakeHttpClient transport = new FakeHttpClient("partial".getBytes(StandardCharsets.UTF_8));
        transport.failBody = true;
        var response = highlightly(transport).fetch(request("highlightly", "fixture", 0));
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("partial".getBytes(StandardCharsets.UTF_8));
        assertThat(response.failureCode()).isEqualTo("UNCERTAIN_RESPONSE");
        assertThat(response.toString()).doesNotContain(CREDENTIAL);
    }

    @Test
    void noResponseIsUncertainAndNeverRetried() {
        FakeHttpClient transport = new FakeHttpClient(new byte[0]);
        transport.failSend = true;
        var response = highlightly(transport).fetch(request("highlightly", "fixture", 0));
        assertThat(response.httpStatus()).isZero();
        assertThat(response.failureCode()).isEqualTo("UNCERTAIN_RESPONSE");
        assertThat(response.body()).isEmpty();
        assertThat(transport.sends).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 401, 403, 429, 500})
    void nonSuccessHttpStatusIsReturnedAsKnownResponseWithoutRetry(int status) {
        FakeHttpClient transport = new FakeHttpClient("{}".getBytes(StandardCharsets.UTF_8));
        transport.status = status;
        var response = highlightly(transport).fetch(request("highlightly", "fixture", 0));
        assertThat(response.httpStatus()).isEqualTo(status);
        assertThat(response.failureCode()).isNull();
        assertThat(transport.sends).isEqualTo(1);
    }

    @Test
    void secretEchoDiscardsEntireBodyEvenWhenResponseIsPartial() {
        FakeHttpClient transport = new FakeHttpClient(("{\"echo\":\"" + CREDENTIAL + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        transport.failBody = true;
        var response = highlightly(transport).fetch(request("highlightly", "fixture", 0));
        assertThat(response.failureCode()).isEqualTo("SECRET_ECHO");
        assertThat(response.httpStatus()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
        assertThat(response.toString()).doesNotContain(CREDENTIAL);
    }

    @Test
    void unicodeEscapedSecretIsDiscardedWithoutRequiringValidJson() {
        String initialEscape = "\\u0073" + CREDENTIAL.substring(1);
        String fullyEscaped = CREDENTIAL.chars()
                .mapToObj(character -> "\\u" + "%04x".formatted(character))
                .collect(java.util.stream.Collectors.joining());
        for (String body : List.of(
                "{\"echo\":\"" + initialEscape + "\"}",
                "{\"echo\":\"" + fullyEscaped + "\"}",
                "{invalid-json echo=" + initialEscape)) {
            var transport = new FakeHttpClient(body.getBytes(StandardCharsets.UTF_8));
            var response = highlightly(transport).fetch(request("highlightly", "fixture", 0));
            assertThat(response.failureCode()).isEqualTo("SECRET_ECHO");
            assertThat(response.body()).isEmpty();
            assertThat(response.toString()).doesNotContain(CREDENTIAL);
        }
    }

    @Test
    void mixedUnicodeAndOrdinaryJsonEscapesDoNotHideCredentialCharacters() {
        String unusualCredential = "synthetic-\"quoted\"/value";
        String echoed = "{\"echo\":\"synthetic-\\u0022quoted\\\"\\/value\"}";
        var transport = new FakeHttpClient(echoed.getBytes(StandardCharsets.UTF_8));
        var client = new HighlightlyCalendarPageClient(transport, unusualCredential, CLOCK);
        var response = client.fetch(request("highlightly", "fixture", 0));
        assertThat(response.failureCode()).isEqualTo("SECRET_ECHO");
        assertThat(response.body()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "", "12.0", "abc", "9223372036854775808", "9 0"})
    void malformedQuotaIsAbsentNotZero(String quota) {
        FakeHttpClient transport = new FakeHttpClient(new byte[0]);
        transport.headers = Map.of("x-ratelimit-requests-remaining", List.of(quota));
        assertThat(highlightly(transport).fetch(request("highlightly", "fixture", 0)).quotaRemaining()).isNull();
    }

    @Test
    void duplicatedQuotaIsNotArbitrarilySelected() {
        FakeHttpClient transport = new FakeHttpClient(new byte[0]);
        transport.headers = Map.of("x-ratelimit-requests-remaining", List.of("90", "80"));
        assertThat(highlightly(transport).fetch(request("highlightly", "fixture", 0)).quotaRemaining()).isNull();
    }

    @Test
    void openTransactionOrMismatchedProviderCannotSend() {
        FakeHttpClient transport = new FakeHttpClient(new byte[0]);
        var client = highlightly(transport);
        assertThatThrownBy(() -> client.fetch(request("football-data.org", "PD", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> client.fetch(request("highlightly", "fixture", 0)))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(transport.sends).isZero();
    }

    @Test
    void responsePayloadIsDefensivelyCopiedAndFailureCodeCannotLeakText() {
        byte[] input = {1, 2};
        var response = new CalendarPageResponse(NOW, NOW, 200, input, null, null);
        input[0] = 3;
        byte[] returned = response.body();
        returned[0] = 4;
        assertThat(response.body()).containsExactly((byte) 1, (byte) 2);
        assertThatThrownBy(() -> new CalendarPageResponse(NOW, NOW, 200, input, null, "not a generic code"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarPageResponse(NOW, NOW, 0, input, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestRejectsUnsupportedProviderFamilyAndInvalidBounds() {
        assertThatThrownBy(() -> request("other", "PD", 0)).isInstanceOf(IllegalArgumentException.class);
        var matchDetail = new ProviderCapabilityKey("highlightly", "fixture", "2026", "regular", CapabilityDataType.MATCH_DETAIL);
        assertThatThrownBy(() -> new CalendarPageRequest(matchDetail, LocalDate.now(CLOCK), 2026, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        var key = key("highlightly", "fixture");
        assertThatThrownBy(() -> new CalendarPageRequest(key, null, 2026, 0, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CalendarPageRequest(key, LocalDate.now(CLOCK), 999, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarPageRequest(key, LocalDate.now(CLOCK), 10000, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarPageRequest(key, LocalDate.now(CLOCK), 2026, -1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarPageRequest(key, LocalDate.now(CLOCK), 2026, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarPageRequest(key, LocalDate.now(CLOCK), 2026, 0, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertDisabled(CalendarPageClient client) {
        assertThat(client.available()).isFalse();
        var response = client.fetch(request(client.provider(), "fixture", 0));
        assertThat(response.httpStatus()).isZero();
        assertThat(response.body()).isEmpty();
        assertThat(response.failureCode()).isEqualTo("PROVIDER_DISABLED");
    }

    private static ProviderCapabilityKey key(String provider, String competition) {
        return new ProviderCapabilityKey(provider, competition, "2026", "regular", CapabilityDataType.CALENDAR);
    }

    private static CalendarPageRequest request(String provider, String competition, int offset) {
        return new CalendarPageRequest(key(provider, competition), LocalDate.now(CLOCK), 2026, offset, 20);
    }

    private static HighlightlyCalendarPageClient highlightly(FakeHttpClient client) {
        return new HighlightlyCalendarPageClient(client, CREDENTIAL, CLOCK);
    }

    private static HighlightlyCalendarPageClient bounded(FakeHttpClient client, Duration timeout, int maximum) {
        return new HighlightlyCalendarPageClient(client, CREDENTIAL, CLOCK, timeout, maximum, HighlightlyCalendarPageClient.BASE_URI);
    }

    private static final class FakeHttpClient extends HttpClient {
        final byte[] bytes;
        Map<String, List<String>> headers = Map.of();
        HttpRequest request;
        int status = 200;
        int sends;
        boolean cancelled;
        boolean neverComplete;
        boolean failBody;
        boolean failSend;

        FakeHttpClient(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest submitted, HttpResponse.BodyHandler<T> handler) {
            sends++;
            request = submitted;
            if (failSend) {
                return CompletableFuture.failedFuture(new IOException("synthetic failure " + CREDENTIAL));
            }
            var responseHeaders = HttpHeaders.of(headers, (name, value) -> true);
            HttpResponse.BodySubscriber<T> subscriber = handler.apply(new HttpResponse.ResponseInfo() {
                @Override public int statusCode() { return status; }
                @Override public HttpHeaders headers() { return responseHeaders; }
                @Override public Version version() { return Version.HTTP_1_1; }
            });
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override public void request(long count) { }
                @Override public void cancel() { cancelled = true; }
            });
            subscriber.onNext(List.of(ByteBuffer.wrap(bytes)));
            if (failBody) {
                subscriber.onError(new IOException("synthetic failure " + CREDENTIAL));
            } else if (!neverComplete && !cancelled) {
                subscriber.onComplete();
            }
            return subscriber.getBody().toCompletableFuture().thenApply(body -> new HttpResponse<>() {
                @Override public int statusCode() { return status; }
                @Override public HttpRequest request() { return submitted; }
                @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
                @Override public HttpHeaders headers() { return responseHeaders; }
                @Override public T body() { return body; }
                @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
                @Override public URI uri() { return submitted.uri(); }
                @Override public Version version() { return Version.HTTP_1_1; }
            });
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushes) { throw new UnsupportedOperationException(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) { throw new UnsupportedOperationException(); }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(5)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new UnsupportedOperationException(); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
    }
}
