package com.bettingproject.collection.domain.capability;

final class CapabilityText {
    private CapabilityText() {
    }

    static String exact(String value, String field, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.strip())
                || value.length() > maximum || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid capability field: " + field);
        }
        return value;
    }

    static void assignment(String value) {
        if (value.contains("*") || value.contains("?") || value.contains("%")) {
            throw new IllegalArgumentException("wildcards forbidden in capability assignments");
        }
    }
}
