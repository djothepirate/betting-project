package com.bettingproject.collection.adapter.http.enrichment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;

import com.bettingproject.collection.application.enrichment.EnrichmentProviderRequest;
import com.bettingproject.enrichment.domain.EnrichmentFamily;

/** Bounded Highlightly transport for the exact native enrichment endpoints. */
public final class HighlightlyEnrichmentClient extends BoundedEnrichmentHttpClient {
    public static final URI BASE_URI = URI.create("https://soccer.highlightly.net/");
    public static final String PROVIDER = "highlightly";
    public static final String CONNECTOR_VERSION = "highlightly-enrichment-http-v1";
    private final URI baseUri;

    HighlightlyEnrichmentClient(HttpClient client, String credential, Clock clock) {
        this(client, credential, clock, REQUEST_TIMEOUT, MAX_RESPONSE_BYTES);
    }

    HighlightlyEnrichmentClient(HttpClient client, String credential, Clock clock, Duration timeout, int maximum) {
        this(client, credential, clock, timeout, maximum, BASE_URI);
    }

    HighlightlyEnrichmentClient(HttpClient client, String credential, Clock clock, Duration timeout, int maximum,
            URI baseUri) {
        super(client, credential, clock, timeout, maximum);
        this.baseUri = validatedBaseUri(baseUri, BASE_URI.getHost());
    }

    @Override public String provider() { return PROVIDER; }
    @Override public String connectorVersion() { return CONNECTOR_VERSION; }
    @Override public boolean supports(EnrichmentFamily family) { return family != null; }

    @Override
    URI requestUri(EnrichmentProviderRequest request) {
        String endpoint = switch (request.family()) {
            case MATCH_DETAIL -> "matches";
            case LINEUP -> "lineups";
            case TEAM_STATS -> "statistics";
            case EVENTS -> "events";
            case PLAYER_STATS -> "box-score";
        };
        return baseUri.resolve(endpoint + "/" + request.providerFixtureId());
    }

    @Override
    void authenticate(HttpRequest.Builder builder, String credential) {
        builder.header("x-rapidapi-key", credential).header("x-rapidapi-host", BASE_URI.getHost());
    }

    @Override
    Long quota(HttpResponse<?> response) {
        var values = response.headers().allValues("x-ratelimit-requests-remaining");
        if (values.size() != 1 || !values.getFirst().matches("[0-9]{1,19}")) { return null; }
        try { return Long.parseLong(values.getFirst()); }
        catch (NumberFormatException invalid) { return null; }
    }
}
