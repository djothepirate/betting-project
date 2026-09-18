package com.bettingproject.collection.adapter.http.calendar;

import com.bettingproject.collection.application.calendar.CalendarPageClient;
import com.bettingproject.collection.application.calendar.CalendarPageRequest;
import com.bettingproject.collection.application.calendar.CalendarPageResponse;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transport only: the caller must first obtain a committed budget authorization. */
abstract class BoundedCalendarHttpClient implements CalendarPageClient, AutoCloseable {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);
    static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;

    private final HttpClient client;
    private final String secret;
    private final Clock clock;
    private final Duration timeout;
    private final int maximumBytes;
    final URI baseUri;

    BoundedCalendarHttpClient(
            HttpClient client, String secret, Clock clock, Duration timeout, int maximumBytes, URI baseUri) {
        this.client = client;
        this.secret = secret;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero() || maximumBytes < 1) {
            throw new IllegalArgumentException("Invalid transport bounds");
        }
        this.maximumBytes = maximumBytes;
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
    }

    static HttpClient productionTransport() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    static boolean usableSecret(String value) {
        return value != null && !value.isEmpty() && value.length() <= 4096
                && value.equals(value.strip())
                && value.chars().allMatch(character -> character >= 33 && character <= 126);
    }

    @Override
    public final boolean available() {
        return client != null && usableSecret(secret);
    }

    @Override
    public final void close() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public final CalendarPageResponse fetch(CalendarPageRequest page) {
        Objects.requireNonNull(page, "page");
        if (!provider().equals(page.capability().provider())) {
            throw new IllegalArgumentException("Mismatched calendar provider");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Calendar HTTP cannot run inside a transaction");
        }
        Instant started = now();
        if (!available()) {
            return response(started, 0, new byte[0], null, "PROVIDER_DISABLED");
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri(page)).timeout(timeout)
                .header("Accept", "application/json").GET();
        authenticate(builder, secret);
        Capture capture = new Capture(maximumBytes);
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            // Exactly one application-level send: no retry and no redirect orchestration.
            pending = client.sendAsync(builder.build(), info -> {
                capture.httpStatus = info.statusCode();
                capture.remaining = quota(info);
                return capture;
            });
            pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            capture.stop("UNCERTAIN_RESPONSE");
        } catch (ExecutionException | TimeoutException | RuntimeException failure) {
            // Never expose an exception message, URI, credentials or arbitrary response headers.
            capture.stop("UNCERTAIN_RESPONSE");
        } finally {
            if (pending != null && !pending.isDone()) {
                pending.cancel(true);
            }
        }
        byte[] bytes = capture.snapshot();
        if (echoesSecret(bytes)) {
            return response(started, capture.httpStatus, new byte[0], capture.remaining, "SECRET_ECHO");
        }
        return response(started, capture.httpStatus, bytes, capture.remaining, capture.failure());
    }

    abstract URI requestUri(CalendarPageRequest request);

    abstract void authenticate(HttpRequest.Builder builder, String credential);

    Long quota(HttpResponse.ResponseInfo info) {
        return null;
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private CalendarPageResponse response(Instant started, int status, byte[] body, Long quota, String failure) {
        Instant received = now();
        return new CalendarPageResponse(started, received.isBefore(started) ? started : received,
                status, body, quota, failure);
    }

    private boolean echoesSecret(byte[] body) {
        String value = new String(body, StandardCharsets.UTF_8);
        return containsCredential(value) || containsCredential(decodeJsonEscapes(value));
    }

    private boolean containsCredential(String value) {
        String jsonEscaped = secret.replace("\\", "\\\\").replace("\"", "\\\"");
        return value.contains(secret) || value.contains(jsonEscaped) || value.contains(encode(secret));
    }

    /** Scan bounded bytes even when the enclosing JSON is malformed; never rewrite stored evidence. */
    private static String decodeJsonEscapes(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\\' && index + 1 < value.length()) {
                char escaped = value.charAt(index + 1);
                if (escaped == 'u' && index + 5 < value.length()) {
                    int code = 0;
                    boolean valid = true;
                    for (int digit = index + 2; digit <= index + 5; digit++) {
                        int hex = Character.digit(value.charAt(digit), 16);
                        if (hex < 0) {
                            valid = false;
                            break;
                        }
                        code = code * 16 + hex;
                    }
                    if (valid) {
                        decoded.append((char) code);
                        index += 5;
                        continue;
                    }
                }
                Character replacement = switch (escaped) {
                    case '\\', '"', '/' -> escaped;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> null;
                };
                if (replacement != null) {
                    decoded.append(replacement.charValue());
                    index++;
                    continue;
                }
            }
            decoded.append(current);
        }
        return decoded.toString();
    }

    private static final class Capture implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> completed = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private String failure;
        private volatile int httpStatus;
        private volatile Long remaining;

        private Capture(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return completed;
        }

        @Override
        public synchronized void onSubscribe(Flow.Subscription value) {
            if (subscription != null || completed.isDone()) {
                value.cancel();
                return;
            }
            subscription = value;
            value.request(1);
        }

        @Override
        public synchronized void onNext(List<ByteBuffer> items) {
            if (completed.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                int length = Math.min(item.remaining(), maximum - bytes.size());
                byte[] prefix = new byte[length];
                item.get(prefix);
                bytes.writeBytes(prefix);
                if (item.hasRemaining()) {
                    stop("RESPONSE_TOO_LARGE");
                    return;
                }
            }
            subscription.request(1);
        }

        @Override
        public synchronized void onError(Throwable failure) {
            stop("UNCERTAIN_RESPONSE");
        }

        @Override
        public synchronized void onComplete() {
            completed.complete(bytes.toByteArray());
        }

        synchronized void stop(String code) {
            if (failure == null) {
                failure = code;
            }
            if (subscription != null) {
                subscription.cancel();
            }
            completed.complete(bytes.toByteArray());
        }

        synchronized byte[] snapshot() {
            return bytes.toByteArray();
        }

        synchronized String failure() {
            return failure;
        }
    }
}
