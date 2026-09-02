package com.bettingproject.catalog.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.bettingproject.catalog.domain.CanonicalCompetition;
import com.bettingproject.catalog.domain.CanonicalTeam;
import com.bettingproject.catalog.domain.CompetitionType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"control-api", "batch-worker"})
public class CatalogCommandService {

    private final CatalogRepository catalogRepository;
    private final Clock clock;

    public CatalogCommandService(
            CatalogRepository catalogRepository,
            Clock clock) {
        this.catalogRepository = catalogRepository;
        this.clock = clock;
    }

    @Transactional
    public UUID registerCompetition(String name, String countryCode, CompetitionType type) {
        Instant now = clock.instant();
        CanonicalCompetition competition = new CanonicalCompetition(
                UUID.randomUUID(), name, countryCode, type, now, now);
        return catalogRepository.getOrCreateCompetition(competition).id();
    }

    @Transactional
    public UUID registerTeam(String name, String countryCode) {
        Instant now = clock.instant();
        CanonicalTeam team = new CanonicalTeam(UUID.randomUUID(), name, countryCode, now, now);
        return catalogRepository.getOrCreateTeam(team).id();
    }

}
