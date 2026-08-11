package com.bettingproject.catalog.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.bettingproject.catalog.domain.CanonicalCompetition;
import com.bettingproject.catalog.domain.CanonicalTeam;
import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMapping;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"control-api", "batch-worker"})
public class CatalogCommandService {

    private final CatalogRepository catalogRepository;
    private final ProviderMappingRepository mappingRepository;
    private final Clock clock;

    public CatalogCommandService(
            CatalogRepository catalogRepository,
            ProviderMappingRepository mappingRepository,
            Clock clock) {
        this.catalogRepository = catalogRepository;
        this.mappingRepository = mappingRepository;
        this.clock = clock;
    }

    @Transactional
    public UUID registerCompetition(String name, String countryCode, CompetitionType type) {
        return catalogRepository.findCompetition(name.trim(), countryCode.trim().toUpperCase())
                .map(CanonicalCompetition::id)
                .orElseGet(() -> {
                    Instant now = clock.instant();
                    CanonicalCompetition competition = new CanonicalCompetition(
                            UUID.randomUUID(), name, countryCode, type, now, now);
                    catalogRepository.insertCompetition(competition);
                    return competition.id();
                });
    }

    @Transactional
    public UUID registerTeam(String name, String countryCode) {
        return catalogRepository.findTeam(name.trim(), countryCode.trim().toUpperCase())
                .map(CanonicalTeam::id)
                .orElseGet(() -> {
                    Instant now = clock.instant();
                    CanonicalTeam team = new CanonicalTeam(UUID.randomUUID(), name, countryCode, now, now);
                    catalogRepository.insertTeam(team);
                    return team.id();
                });
    }

    @Transactional
    public void confirmMapping(
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            UUID canonicalEntityId,
            String season,
            String phase) {
        requireCanonicalEntity(entityType, canonicalEntityId);
        Instant now = clock.instant();
        mappingRepository.save(ProviderMapping.confirmed(
                provider, entityType, providerEntityId, canonicalEntityId, season, phase, now));
    }

    @Transactional
    public void markAmbiguous(
            String provider,
            ProviderEntityType entityType,
            String providerEntityId,
            String season,
            String phase,
            Double confidence) {
        Instant now = clock.instant();
        mappingRepository.save(ProviderMapping.ambiguous(
                provider, entityType, providerEntityId, season, phase, confidence, now));
    }

    private void requireCanonicalEntity(ProviderEntityType entityType, UUID canonicalEntityId) {
        boolean exists = switch (entityType) {
            case COMPETITION -> catalogRepository.existsCompetition(canonicalEntityId);
            case TEAM -> catalogRepository.existsTeam(canonicalEntityId);
            case FIXTURE -> catalogRepository.existsFixture(canonicalEntityId);
            case SNAPSHOT -> throw new IllegalArgumentException("SNAPSHOT cannot be mapped");
        };
        if (!exists) {
            throw new IllegalArgumentException(
                    "Canonical entity does not exist: " + entityType + "/" + canonicalEntityId);
        }
    }
}
