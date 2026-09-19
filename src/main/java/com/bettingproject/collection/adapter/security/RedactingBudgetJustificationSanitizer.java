package com.bettingproject.collection.adapter.security;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bettingproject.collection.application.budget.BudgetJustificationSanitizer;
import com.bettingproject.shared.infrastructure.security.SensitiveDataRedactor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("control-api")
public class RedactingBudgetJustificationSanitizer implements BudgetJustificationSanitizer {
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?is)-----BEGIN [^-\\r\\n]*PRIVATE KEY-----.*?-----END [^-\\r\\n]*PRIVATE KEY-----");
    // Quoted values may contain whitespace. Incomplete quotes are removed through end-of-input.
    private static final String VALUE = "(?:\"(?:\\\\.|[^\"\\\\])*(?:\"|$)|"
            + "'(?:\\\\.|[^'\\\\])*(?:'|$)|[^\\s,;&]+)";
    private static final Pattern BEARER_CREDENTIAL = Pattern.compile("(?i)(\\bBearer\\s+)" + VALUE);
    private static final Pattern NAMED_CREDENTIAL = Pattern.compile(
            "(?i)(\\b(?:authorization|api[_-]?key|x-api-key|x-rapidapi-key|token|"
                    + "access[_-]?token|client[_-]?secret)\\s*[:=]\\s*)(?:(?:Bearer|Basic)\\s+)?" + VALUE);
    private static final Pattern URL_CREDENTIAL = Pattern.compile(
            "(?i)([?&](?:api[_-]?key|key|token|access_token|client_secret)=)" + VALUE);
    private static final Pattern KNOWN_TOKEN = Pattern.compile(
            "\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})\\b");
    private final SensitiveDataRedactor redactor;

    public RedactingBudgetJustificationSanitizer(SensitiveDataRedactor redactor) {
        this.redactor = redactor;
    }

    @Override
    public String sanitize(String justification) {
        String safe = Objects.requireNonNull(justification);
        safe = PRIVATE_KEY.matcher(safe).replaceAll(Matcher.quoteReplacement(SensitiveDataRedactor.REDACTED));
        safe = BEARER_CREDENTIAL.matcher(safe).replaceAll("$1" + Matcher.quoteReplacement(SensitiveDataRedactor.REDACTED));
        safe = NAMED_CREDENTIAL.matcher(safe).replaceAll("$1" + Matcher.quoteReplacement(SensitiveDataRedactor.REDACTED));
        safe = URL_CREDENTIAL.matcher(safe).replaceAll("$1" + Matcher.quoteReplacement(SensitiveDataRedactor.REDACTED));
        safe = redactor.redactUrl(safe);
        safe = KNOWN_TOKEN.matcher(safe).replaceAll(Matcher.quoteReplacement(SensitiveDataRedactor.REDACTED));
        return safe.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
    }
}
