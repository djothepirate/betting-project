package com.bettingproject.collection.adapter.http.calendar;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** JDK HTTP retry options are process-wide and may be cached: require startup arguments. */
final class CalendarHttpRuntimeSafety {
    static final String DISABLE_RETRY = "jdk.httpclient.disableRetryConnect";
    static final String ATTEMPT_LIMIT = "jdk.httpclient.redirects.retrylimit";
    static final String HTTP_LOG = "jdk.httpclient.HttpClient.log";

    private CalendarHttpRuntimeSafety() {
    }

    static boolean allowed() {
        return allowedStartup(ManagementFactory.getRuntimeMXBean().getInputArguments(), System::getProperty);
    }

    static boolean allowedStartup(List<String> arguments, Function<String, String> currentProperty) {
        Map<String, String> startup = new HashMap<>();
        for (String argument : arguments) {
            for (String key : List.of(DISABLE_RETRY, ATTEMPT_LIMIT, HTTP_LOG)) {
                String prefix = "-D" + key + "=";
                if (argument.startsWith(prefix)) {
                    startup.put(key, argument.substring(prefix.length()));
                }
            }
        }
        return "true".equals(startup.get(DISABLE_RETRY))
                && "1".equals(startup.get(ATTEMPT_LIMIT))
                && "true".equals(currentProperty.apply(DISABLE_RETRY))
                && "1".equals(currentProperty.apply(ATTEMPT_LIMIT))
                && loggingDisabled(startup.get(HTTP_LOG))
                && loggingDisabled(currentProperty.apply(HTTP_LOG));
    }

    private static boolean loggingDisabled(String value) {
        return value == null || value.isEmpty() || value.equals("none");
    }
}
