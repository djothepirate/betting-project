package com.bettingproject.catalog.adapter.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

final class CatalogFilterFingerprint {

    private CatalogFilterFingerprint() {
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private final Map<String, String> values = new LinkedHashMap<>();

        Builder add(String name, Object value) {
            values.put(name, value == null ? "<absent>" : value.toString());
            return this;
        }

        Builder addContext(String name, boolean supplied, String value) {
            values.put(name, supplied ? "<present>:" + (value == null ? "" : value) : "<absent>");
            return this;
        }

        String sha256() {
            StringBuilder canonical = new StringBuilder("catalog-query-filter-v1\n");
            values.forEach((key, value) -> canonical
                    .append(key.length()).append(':').append(key).append('=')
                    .append(value.length()).append(':').append(value).append('\n'));
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
            }
            catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required", exception);
            }
        }
    }
}
