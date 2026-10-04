package com.bettingproject.collection.adapter.web.control;

import java.time.LocalDate;
import java.util.Set;

import com.bettingproject.collection.application.enrichment.EnrichmentQualityQueryService;
import org.springframework.context.annotation.Profile;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, single-day quality and provenance view. */
@RestController
@Profile("control-api")
@RequestMapping("/internal/collection/enrichment")
public class EnrichmentQualityController {
    private final EnrichmentQualityQueryService queries;

    public EnrichmentQualityController(EnrichmentQualityQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/daily")
    public EnrichmentQualityQueryService.DailyQuality daily(@RequestParam MultiValueMap<String, String> raw) {
        CollectionQueryParameters parameters = new CollectionQueryParameters(raw, Set.of("date"));
        LocalDate date = parameters.date("date");
        if (date == null) { throw CollectionWebException.invalid("INVALID_QUERY"); }
        return queries.daily(date);
    }
}
