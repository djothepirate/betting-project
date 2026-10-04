package com.bettingproject.collection.application.enrichment;

/** Safe parser failure code; it deliberately excludes source text and payload content. */
public final class EnrichmentParseException extends RuntimeException {
    private final String code;

    public EnrichmentParseException(String code) {
        super("Enrichment payload is incompatible");
        this.code = requireCode(code);
    }

    public String code() {
        return code;
    }

    private static String requireCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("code is invalid");
        }
        return value;
    }
}
