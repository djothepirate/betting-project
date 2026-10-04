package com.bettingproject.collection.adapter.http.enrichment;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Requires process-startup guards because the JDK HTTP retry switches may be cached globally. */
final class EnrichmentHttpRuntimeSafety {
    static final String DISABLE_RETRY = "jdk.httpclient.disableRetryConnect";
    static final String REDIRECT_LIMIT = "jdk.httpclient.redirects.retrylimit";
    static final String HTTP_LOG = "jdk.httpclient.HttpClient.log";

    private EnrichmentHttpRuntimeSafety() { }

    static boolean allowed() {
        return allowedStartup(ManagementFactory.getRuntimeMXBean().getInputArguments(), System::getProperty);
    }

    static boolean allowedStartup(List<String> args, Function<String, String> current) {
        Map<String, String> startup = new HashMap<>();
        for (String argument : args) {
            for (String key : List.of(DISABLE_RETRY, REDIRECT_LIMIT, HTTP_LOG)) {
                String prefix = "-D" + key + "=";
                if (argument.startsWith(prefix)) { startup.put(key, argument.substring(prefix.length())); }
            }
        }
        return "true".equals(startup.get(DISABLE_RETRY))
                && "1".equals(startup.get(REDIRECT_LIMIT))
                && current.apply(DISABLE_RETRY) != null
                && "true".equals(current.apply(DISABLE_RETRY))
                && "1".equals(current.apply(REDIRECT_LIMIT))
                && quiet(startup.get(HTTP_LOG))
                && quiet(current.apply(HTTP_LOG));
    }

    private static boolean quiet(String value) { return value == null || value.isEmpty() || value.equals("none"); }
}
