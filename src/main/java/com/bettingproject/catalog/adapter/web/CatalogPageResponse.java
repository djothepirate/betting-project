package com.bettingproject.catalog.adapter.web;

import java.util.List;

public record CatalogPageResponse<T>(List<T> items, String nextCursor) {

    public CatalogPageResponse {
        items = List.copyOf(items);
    }
}
