package com.bettingproject.catalog.adapter.web;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.util.MultiValueMap;

final class CatalogQueryParameters {

    private static final Set<String> FORBIDDEN_LOCATION_PARAMETERS = Set.of(
            "path", "uri", "file", "filepath");

    private final MultiValueMap<String, String> parameters;

    private CatalogQueryParameters(MultiValueMap<String, String> parameters) {
        this.parameters = parameters;
    }

    static CatalogQueryParameters validate(
            MultiValueMap<String, String> parameters,
            Set<String> allowed) {
        for (String name : parameters.keySet()) {
            if (FORBIDDEN_LOCATION_PARAMETERS.contains(name.toLowerCase(Locale.ROOT))) {
                throw CatalogWebException.badRequest("ARBITRARY_PATH_FORBIDDEN");
            }
            if (!allowed.contains(name)) {
                throw CatalogWebException.badRequest("UNKNOWN_QUERY_PARAMETER");
            }
            if (parameters.get(name) == null || parameters.get(name).size() != 1) {
                throw CatalogWebException.badRequest("DUPLICATE_QUERY_PARAMETER");
            }
        }
        return new CatalogQueryParameters(parameters);
    }

    boolean contains(String name) {
        return parameters.containsKey(name);
    }

    String optional(String name) {
        if (!parameters.containsKey(name)) {
            return null;
        }
        String value = parameters.getFirst(name);
        return value == null ? "" : value.trim();
    }

    String optionalNonBlank(String name, int maximumLength) {
        String value = optional(name);
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.length() > maximumLength || containsControl(value)) {
            throw CatalogWebException.badRequest("INVALID_QUERY_PARAMETER");
        }
        return value;
    }

    String optionalContext(String name, int maximumLength) {
        String value = optional(name);
        if (value == null) {
            return null;
        }
        if (value.length() > maximumLength || containsControl(value)) {
            throw CatalogWebException.badRequest("INVALID_QUERY_PARAMETER");
        }
        return value;
    }

    UUID optionalUuid(String name) {
        String value = optionalNonBlank(name, 36);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException exception) {
            throw CatalogWebException.badRequest("INVALID_QUERY_PARAMETER");
        }
    }

    <E extends Enum<E>> E optionalEnum(String name, Class<E> type) {
        String value = optionalNonBlank(name, 64);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        }
        catch (IllegalArgumentException exception) {
            throw CatalogWebException.badRequest("INVALID_QUERY_PARAMETER");
        }
    }

    int limit() {
        String value = optional("limit");
        if (value == null) {
            return 50;
        }
        try {
            int limit = Integer.parseInt(value);
            if (limit < 1 || limit > 100) {
                throw CatalogWebException.badRequest("INVALID_LIMIT");
            }
            return limit;
        }
        catch (NumberFormatException exception) {
            throw CatalogWebException.badRequest("INVALID_LIMIT");
        }
    }

    String cursor() {
        String value = optional("cursor");
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.length() > 2_048 || containsControl(value)) {
            throw CatalogWebException.badRequest("INVALID_CURSOR");
        }
        return value;
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
