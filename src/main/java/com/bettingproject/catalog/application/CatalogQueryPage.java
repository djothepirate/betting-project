package com.bettingproject.catalog.application;

import java.util.List;
import java.util.Objects;

public record CatalogQueryPage<T>(List<T> items, boolean hasNext) {

    public CatalogQueryPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public static <T> CatalogQueryPage<T> fromFetched(List<T> fetched, int requestedLimit) {
        Objects.requireNonNull(fetched, "fetched");
        if (requestedLimit < 1 || requestedLimit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        boolean hasNext = fetched.size() > requestedLimit;
        int returnedSize = Math.min(fetched.size(), requestedLimit);
        return new CatalogQueryPage<>(fetched.subList(0, returnedSize), hasNext);
    }
}
