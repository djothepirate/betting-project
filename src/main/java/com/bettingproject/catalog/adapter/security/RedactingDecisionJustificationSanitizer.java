package com.bettingproject.catalog.adapter.security;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bettingproject.catalog.application.DecisionJustificationSanitizer;
import com.bettingproject.shared.infrastructure.security.SensitiveDataRedactor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("control-api")
public class RedactingDecisionJustificationSanitizer
        implements DecisionJustificationSanitizer {

    static final String REDACTED = SensitiveDataRedactor.REDACTED;

    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            "(?is)-----BEGIN [^-\\r\\n]*PRIVATE KEY-----.*?"
                    + "-----END [^-\\r\\n]*PRIVATE KEY-----");
    private static final Pattern BEARER_CREDENTIAL = Pattern.compile(
            "(?i)(\\bBearer\\s+)[^\\s,;]+");
    private static final Pattern NAMED_CREDENTIAL = Pattern.compile(
            "(?i)(\\b(?:authorization|api[_-]?key|x-api-key|x-rapidapi-key|token|"
                    + "access[_-]?token|client[_-]?secret)\\s*[:=]\\s*)([^\\s,;&]+)");
    private static final Pattern URL_CREDENTIAL = Pattern.compile(
            "(?i)([?&](?:api[_-]?key|key|token|access_token|client_secret)=)[^&#\\s]*");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Pattern REPEATED_WHITESPACE = Pattern.compile("\\s+");

    private final SensitiveDataRedactor sensitiveDataRedactor;

    public RedactingDecisionJustificationSanitizer(
            SensitiveDataRedactor sensitiveDataRedactor) {
        this.sensitiveDataRedactor = sensitiveDataRedactor;
    }

    @Override
    public String sanitize(String rawJustification) {
        String sanitized = Objects.requireNonNull(rawJustification, "rawJustification");
        sanitized = sensitiveDataRedactor.redactUrl(sanitized);
        sanitized = PRIVATE_KEY_BLOCK.matcher(sanitized)
                .replaceAll(Matcher.quoteReplacement(REDACTED));
        sanitized = replaceCredentialValue(BEARER_CREDENTIAL, sanitized);
        sanitized = replaceCredentialValue(NAMED_CREDENTIAL, sanitized);
        sanitized = replaceCredentialValue(URL_CREDENTIAL, sanitized);
        sanitized = CONTROL_CHARACTER.matcher(sanitized).replaceAll(" ");
        return REPEATED_WHITESPACE.matcher(sanitized).replaceAll(" ").strip();
    }

    private String replaceCredentialValue(Pattern pattern, String value) {
        return pattern.matcher(value).replaceAll("$1" + Matcher.quoteReplacement(REDACTED));
    }
}
