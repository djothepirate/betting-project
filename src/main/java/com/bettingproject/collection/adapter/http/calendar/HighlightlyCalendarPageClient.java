package com.bettingproject.collection.adapter.http.calendar;

import com.bettingproject.collection.application.calendar.CalendarPageRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;

public final class HighlightlyCalendarPageClient extends BoundedCalendarHttpClient {
    static final URI BASE_URI = URI.create("https://soccer.highlightly.net/");

    HighlightlyCalendarPageClient(HttpClient client, String secret, Clock clock) {
        this(client, secret, clock, RESPONSE_TIMEOUT, MAX_RESPONSE_BYTES, BASE_URI);
    }

    // Package-private injection is reserved for offline/fake and loopback transport tests.
    HighlightlyCalendarPageClient(
            HttpClient client, String secret, Clock clock, Duration timeout, int maximum, URI baseUri) {
        super(client, secret, clock, timeout, maximum, baseUri);
    }

    @Override
    public String provider() {
        return "highlightly";
    }

    @Override
    URI requestUri(CalendarPageRequest request) {
        return baseUri.resolve("matches?leagueId=" + encode(request.capability().providerCompetitionId())
                + "&season=" + request.seasonStartYear() + "&date=" + request.date()
                + "&timezone=Etc%2FUTC&limit=" + request.limit() + "&offset=" + request.offset());
    }

    @Override
    void authenticate(HttpRequest.Builder builder, String credential) {
        builder.header("x-rapidapi-key", credential).header("x-rapidapi-host", BASE_URI.getHost());
    }

    @Override
    Long quota(HttpResponse.ResponseInfo info) {
        var values = info.headers().allValues("x-ratelimit-requests-remaining");
        if (values.size() != 1 || !values.getFirst().matches("[0-9]{1,19}")) {
            return null;
        }
        try {
            return Long.parseLong(values.getFirst());
        } catch (NumberFormatException invalidCounter) {
            return null;
        }
    }
}
