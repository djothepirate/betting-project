package com.bettingproject.collection.adapter.web.j7;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects unbounded or encoded request bodies before Spring allocates a byte array for them.
 */
@Component
@Profile("control-api")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(
        prefix = "betting.integration.j7-receiver",
        name = "enabled",
        havingValue = "true")
public class J7RequestEnvelopeFilter extends OncePerRequestFilter {

    private static final byte[] INVALID_ENVELOPE = problem(
            "INVALID_REQUEST_ENVELOPE",
            "The request framing does not match the J7 import contract.",
            HttpServletResponse.SC_BAD_REQUEST);
    private static final byte[] METHOD_NOT_ALLOWED = problem(
            "METHOD_NOT_ALLOWED",
            "Only POST is accepted by the J7 import contract.",
            HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    private static final byte[] PAYLOAD_TOO_LARGE = problem(
            "PAYLOAD_TOO_LARGE",
            "The J7 import body exceeds the fixed protocol limit.",
            HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !J7ImportHttpContract.belongsToReceiverRouteFamily(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!J7ImportHttpContract.PATH.equals(request.getRequestURI())) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_ENVELOPE);
            return;
        }
        if (!J7ImportHttpContract.METHOD.equals(request.getMethod())) {
            response.setHeader("Allow", J7ImportHttpContract.METHOD);
            reject(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED);
            return;
        }
        if (request.getQueryString() != null) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_ENVELOPE);
            return;
        }
        if (!singleAbsentHeader(request, J7ImportHttpContract.TRANSFER_ENCODING)
                || !identityOrAbsentContentEncoding(request)) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_ENVELOPE);
            return;
        }
        Long declaredLength = declaredLength(request);
        if (declaredLength == null || declaredLength < 1) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, INVALID_ENVELOPE);
            return;
        }
        if (declaredLength > J7ImportHttpContract.MAXIMUM_REQUEST_BYTES) {
            reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, PAYLOAD_TOO_LARGE);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Long declaredLength(HttpServletRequest request) {
        List<String> values = headerValues(request, J7ImportHttpContract.CONTENT_LENGTH);
        if (values.size() != 1) {
            return null;
        }
        String value = values.getFirst();
        if (value == null || value.isEmpty() || value.length() > 7
                || value.chars().anyMatch(character -> character < '0' || character > '9')) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            long servletLength = request.getContentLengthLong();
            return servletLength >= 0 && servletLength != parsed ? null : parsed;
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean singleAbsentHeader(HttpServletRequest request, String name) {
        return headerValues(request, name).isEmpty();
    }

    private boolean identityOrAbsentContentEncoding(HttpServletRequest request) {
        List<String> values = headerValues(request, J7ImportHttpContract.CONTENT_ENCODING);
        return values.isEmpty() || (values.size() == 1 && "identity".equals(values.getFirst()));
    }

    private List<String> headerValues(HttpServletRequest request, String name) {
        return Collections.list(request.getHeaders(name));
    }

    private void reject(HttpServletResponse response, int status, byte[] body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static byte[] problem(String code, String detail, int status) {
        return ("{\"type\":\"urn:betting-project:problem:" + code
                + "\",\"title\":\"Invalid J7 import request\",\"status\":" + status + ","
                + "\"detail\":\"" + detail + "\",\"code\":\"" + code + "\","
                + "\"instance\":\"" + J7ImportHttpContract.PATH + "\"}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
