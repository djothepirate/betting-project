package com.bettingproject.shared.infrastructure.security;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class SensitiveDataRedactor {

    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-rapidapi-key");

    private static final Pattern SENSITIVE_QUERY_PARAMETER = Pattern.compile(
            "(?i)([?&](?:api[_-]?key|key|token|access_token|client_secret)=)[^&#\\s]*");

    public Map<String, String> redactHeaders(Map<String, String> headers) {
        Map<String, String> redacted = new LinkedHashMap<>();
        headers.forEach((name, value) -> redacted.put(
                name,
                SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT)) ? REDACTED : value));
        return Map.copyOf(redacted);
    }

    public String redactUrl(String url) {
        return SENSITIVE_QUERY_PARAMETER.matcher(url).replaceAll("$1" + REDACTED);
    }
}
