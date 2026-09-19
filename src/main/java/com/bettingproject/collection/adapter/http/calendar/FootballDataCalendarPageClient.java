package com.bettingproject.collection.adapter.http.calendar;

import com.bettingproject.collection.application.calendar.CalendarPageRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.Duration;

public final class FootballDataCalendarPageClient extends BoundedCalendarHttpClient {
    static final URI BASE_URI = URI.create("https://api.football-data.org/v4/");

    FootballDataCalendarPageClient(HttpClient client, String secret, Clock clock) {
        this(client, secret, clock, RESPONSE_TIMEOUT, MAX_RESPONSE_BYTES, BASE_URI);
    }

    FootballDataCalendarPageClient(
            HttpClient client, String secret, Clock clock, Duration timeout, int maximum, URI baseUri) {
        super(client, secret, clock, timeout, maximum, baseUri);
    }

    @Override
    public String provider() {
        return "football-data.org";
    }

    @Override
    URI requestUri(CalendarPageRequest request) {
        if (request.offset() != 0) {
            throw new IllegalArgumentException("Football-data calendar has no offset pagination");
        }
        String competition = encode(request.capability().providerCompetitionId()).replace(".", "%2E");
        return baseUri.resolve("competitions/" + competition
                + "/matches?season=" + request.seasonStartYear() + "&dateFrom=" + request.date()
                + "&dateTo=" + request.date().plusDays(1));
    }

    @Override
    void authenticate(HttpRequest.Builder builder, String credential) {
        builder.header("X-Auth-Token", credential);
    }
}
