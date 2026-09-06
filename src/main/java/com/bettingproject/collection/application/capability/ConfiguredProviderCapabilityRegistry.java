package com.bettingproject.collection.application.capability;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.bettingproject.collection.domain.capability.CapabilityAuthorityRole;
import com.bettingproject.collection.domain.capability.CapabilityRouteKey;
import com.bettingproject.collection.domain.capability.ProviderCapability;
import com.bettingproject.collection.domain.capability.ProviderCapabilityKey;

public final class ConfiguredProviderCapabilityRegistry implements ProviderCapabilityRegistry {
    private final String registryVersion;
    private final String documentSha256;
    private final Map<ProviderCapabilityKey, ProviderCapability> entries;
    private final Map<CapabilityRouteKey, List<ProviderCapability>> routes;

    public ConfiguredProviderCapabilityRegistry(
            String registryVersion, String documentSha256, List<ProviderCapability> entries) {
        if (registryVersion == null || registryVersion.isBlank()
                || !registryVersion.equals(registryVersion.strip()) || registryVersion.length() > 64
                || registryVersion.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid registry version");
        }
        if (documentSha256 == null || !documentSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid registry document hash");
        }
        this.registryVersion = registryVersion;
        this.documentSha256 = documentSha256;
        Map<ProviderCapabilityKey, ProviderCapability> indexed = new HashMap<>();
        Set<CapabilityRouteKey> primaryRoutes = new HashSet<>();
        for (ProviderCapability entry : List.copyOf(Objects.requireNonNull(entries, "entries"))) {
            if (indexed.putIfAbsent(entry.key(), entry) != null) {
                throw new IllegalArgumentException("duplicate provider capability key");
            }
            if (entry.operational() && entry.authorityRole() == CapabilityAuthorityRole.PRIMARY
                    && !primaryRoutes.add(entry.route())) {
                throw new IllegalArgumentException("multiple operational primaries for route");
            }
        }
        this.entries = Map.copyOf(indexed);
        Comparator<ProviderCapability> order = Comparator
                .comparing((ProviderCapability entry) -> entry.key().provider())
                .thenComparing(entry -> entry.key().providerCompetitionId())
                .thenComparing(entry -> entry.key().sourceSeason())
                .thenComparing(entry -> entry.key().sourcePhase());
        Map<CapabilityRouteKey, List<ProviderCapability>> byRoute = new HashMap<>();
        for (ProviderCapability entry : indexed.values()) {
            byRoute.computeIfAbsent(entry.route(), ignored -> new java.util.ArrayList<>()).add(entry);
        }
        byRoute.replaceAll((route, candidates) -> candidates.stream().sorted(order).toList());
        this.routes = Map.copyOf(byRoute);
    }

    @Override
    public String registryVersion() {
        return registryVersion;
    }

    @Override
    public String documentSha256() {
        return documentSha256;
    }

    @Override
    public Optional<ProviderCapability> find(ProviderCapabilityKey key) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(key, "key")));
    }

    @Override
    public List<ProviderCapability> candidates(CapabilityRouteKey route) {
        return routes.getOrDefault(Objects.requireNonNull(route, "route"), List.of());
    }
}
