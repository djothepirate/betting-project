package com.bettingproject.collection.adapter.http.enrichment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Clock;

import com.bettingproject.collection.application.enrichment.EnrichmentProviderRequest;
import com.bettingproject.enrichment.domain.EnrichmentFamily;

/** football-data.org is calendar/detail-only in the measured baseline; enrichment adapters expose MATCH_DETAIL only. */
public final class FootballDataEnrichmentClient extends BoundedEnrichmentHttpClient {
    public static final URI BASE_URI = URI.create("https://api.football-data.org/v4/");
    public static final String PROVIDER = "football-data.org";
    public static final String CONNECTOR_VERSION = "football-data-enrichment-http-v1";
    private final URI baseUri;

    FootballDataEnrichmentClient(HttpClient client, String credential, Clock clock) {
        this(client, credential, clock, REQUEST_TIMEOUT, MAX_RESPONSE_BYTES, BASE_URI);
    }

    FootballDataEnrichmentClient(HttpClient client, String credential, Clock clock, java.time.Duration timeout,
            int maximum, URI baseUri) {
        super(client, credential, clock, timeout, maximum);
        this.baseUri = validatedBaseUri(baseUri, BASE_URI.getHost());
    }

    @Override public String provider() { return PROVIDER; }
    @Override public String connectorVersion() { return CONNECTOR_VERSION; }
    @Override public boolean supports(EnrichmentFamily family) { return family == EnrichmentFamily.MATCH_DETAIL; }

    @Override
    URI requestUri(EnrichmentProviderRequest request) {
        if (request.family() != EnrichmentFamily.MATCH_DETAIL) {
            throw new IllegalArgumentException("football-data.org enrichment family is unsupported");
        }
        return baseUri.resolve("matches/" + request.providerFixtureId());
    }

    @Override
    void authenticate(HttpRequest.Builder builder, String credential) {
        builder.header("X-Auth-Token", credential);
    }
}
