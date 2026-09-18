package com.bettingproject.collection.application.calendar;

/** Safe classification: never carries the response body or a nested parser exception. */
public final class CalendarPageParseException extends IllegalArgumentException {
    private final String code;

    public CalendarPageParseException(String code) {
        super("Provider calendar page could not be interpreted: " + checked(code));
        this.code = code;
    }

    public String code() {
        return code;
    }

    private static String checked(String value) {
        return switch (value) {
            case "INCOMPATIBLE", "INCOMPLETE_PAGINATION", "UNSUPPORTED_STATUS", "INTEGRITY_ERROR" -> value;
            default -> throw new IllegalArgumentException("invalid calendar parsing classification");
        };
    }
}
