package com.bettingproject.collection.adapter.http.calendar;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarHttpRuntimeSafetyTest {
    private static final List<String> SAFE_ARGUMENTS = List.of(
            "-Djdk.httpclient.disableRetryConnect=true", "-Djdk.httpclient.redirects.retrylimit=1");
    private static final Map<String, String> SAFE_PROPERTIES = Map.of(
            CalendarHttpRuntimeSafety.DISABLE_RETRY, "true", CalendarHttpRuntimeSafety.ATTEMPT_LIMIT, "1");

    @Test
    void actualStartupAndCurrentOptionsAreBothRequired() {
        assertThat(CalendarHttpRuntimeSafety.allowedStartup(SAFE_ARGUMENTS, SAFE_PROPERTIES::get)).isTrue();
        assertThat(CalendarHttpRuntimeSafety.allowedStartup(List.of(), SAFE_PROPERTIES::get)).isFalse();
        assertThat(CalendarHttpRuntimeSafety.allowedStartup(SAFE_ARGUMENTS, key -> null)).isFalse();
        assertThat(CalendarHttpRuntimeSafety.allowedStartup(SAFE_ARGUMENTS,
                key -> key.equals(CalendarHttpRuntimeSafety.ATTEMPT_LIMIT) ? "5" : SAFE_PROPERTIES.get(key))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"headers", "requests,headers", "content", "all"})
    void httpLoggingCannotExposeCredentialsWhenConnectorsAreActivated(String value) {
        assertThat(CalendarHttpRuntimeSafety.allowedStartup(SAFE_ARGUMENTS,
                key -> key.equals(CalendarHttpRuntimeSafety.HTTP_LOG) ? value : SAFE_PROPERTIES.get(key))).isFalse();
        var unsafe = List.of(SAFE_ARGUMENTS.getFirst(), SAFE_ARGUMENTS.getLast(),
                "-D" + CalendarHttpRuntimeSafety.HTTP_LOG + "=" + value);
        assertThat(CalendarHttpRuntimeSafety.allowedStartup(unsafe, SAFE_PROPERTIES::get)).isFalse();
    }

    @Test
    void missingOrContradictoryStartupOptionsStayClosed() {
        assertThat(CalendarHttpRuntimeSafety.allowedStartup(List.of(SAFE_ARGUMENTS.getFirst()), SAFE_PROPERTIES::get)).isFalse();
        assertThat(CalendarHttpRuntimeSafety.allowedStartup(List.of(SAFE_ARGUMENTS.getLast()), SAFE_PROPERTIES::get)).isFalse();
        assertThat(CalendarHttpRuntimeSafety.allowedStartup(List.of(
                SAFE_ARGUMENTS.getFirst(), SAFE_ARGUMENTS.getLast(), "-Djdk.httpclient.redirects.retrylimit=5"),
                SAFE_PROPERTIES::get)).isFalse();
    }
}
