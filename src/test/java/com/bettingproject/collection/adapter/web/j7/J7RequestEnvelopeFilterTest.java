package com.bettingproject.collection.adapter.web.j7;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class J7RequestEnvelopeFilterTest {

    private final J7RequestEnvelopeFilter filter = new J7RequestEnvelopeFilter();

    @Test
    void acceptsOnlyADeclaredBoundedIdentityBody() throws Exception {
        MockHttpServletRequest request = requestWithLength("1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void refusesMissingDuplicateOrMalformedContentLength() throws Exception {
        assertRejected(new MockHttpServletRequest(
                J7ImportHttpContract.METHOD, J7ImportHttpContract.PATH), 400);

        MockHttpServletRequest duplicate = requestWithLength("1");
        duplicate.addHeader(J7ImportHttpContract.CONTENT_LENGTH, "1");
        assertRejected(duplicate, 400);

        assertRejected(requestWithLength("+1"), 400);
        assertRejected(requestWithLength("0"), 400);
    }

    @Test
    void refusesAnOversizedBodyBeforeControllerAllocation() throws Exception {
        MockHttpServletRequest request = requestWithLength(
                Integer.toString(J7ImportHttpContract.MAXIMUM_REQUEST_BYTES + 1));

        assertRejected(request, 413);
    }

    @Test
    void refusesTransferEncodingCompressionAndOtherMethods() throws Exception {
        MockHttpServletRequest chunked = requestWithLength("1");
        chunked.addHeader(J7ImportHttpContract.TRANSFER_ENCODING, "chunked");
        assertRejected(chunked, 400);

        MockHttpServletRequest compressed = requestWithLength("1");
        compressed.addHeader(J7ImportHttpContract.CONTENT_ENCODING, "gzip");
        assertRejected(compressed, 400);

        MockHttpServletRequest get = new MockHttpServletRequest(
                "GET", J7ImportHttpContract.PATH);
        assertThat(assertRejected(get, 405).getHeader("Allow")).isEqualTo("POST");

        MockHttpServletRequest query = requestWithLength("1");
        query.setQueryString("unexpected=true");
        assertRejected(query, 400);

        MockHttpServletRequest matrixAlias = new MockHttpServletRequest(
                J7ImportHttpContract.METHOD,
                J7ImportHttpContract.PATH + ";unexpected=true");
        matrixAlias.addHeader(J7ImportHttpContract.CONTENT_LENGTH, "1");
        assertRejected(matrixAlias, 400);

        MockHttpServletRequest trailingSegment = new MockHttpServletRequest(
                J7ImportHttpContract.METHOD,
                J7ImportHttpContract.PATH + "/unexpected");
        trailingSegment.addHeader(J7ImportHttpContract.CONTENT_LENGTH, "1");
        assertRejected(trailingSegment, 400);

        MockHttpServletRequest firstSegmentMatrixAlias = new MockHttpServletRequest(
                J7ImportHttpContract.METHOD,
                "/api;unexpected=true/imports/sofascore/j7-canonical-events");
        firstSegmentMatrixAlias.addHeader(J7ImportHttpContract.CONTENT_LENGTH, "1");
        assertRejected(firstSegmentMatrixAlias, 400);

        MockHttpServletRequest intermediateMatrixAlias = new MockHttpServletRequest(
                J7ImportHttpContract.METHOD,
                "/api/imports;unexpected=true/sofascore/j7-canonical-events");
        intermediateMatrixAlias.addHeader(J7ImportHttpContract.CONTENT_LENGTH, "1");
        assertRejected(intermediateMatrixAlias, 400);

        MockHttpServletRequest encodedAlias = new MockHttpServletRequest(
                J7ImportHttpContract.METHOD,
                "/api/imports/sofascore/%6a7-canonical-events");
        encodedAlias.addHeader(J7ImportHttpContract.CONTENT_LENGTH, "1");
        assertRejected(encodedAlias, 400);
    }

    @Test
    void ignoresUnrelatedControlApiRoutes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsReceiverAliasesBehindContextOrServletPrefixes() throws Exception {
        MockHttpServletRequest contextPrefixed = new MockHttpServletRequest(
                J7ImportHttpContract.METHOD,
                "/context" + J7ImportHttpContract.PATH);
        contextPrefixed.setContextPath("/context");
        contextPrefixed.addHeader(J7ImportHttpContract.CONTENT_LENGTH, "1");
        assertRejected(contextPrefixed, 400);

        MockHttpServletRequest servletPrefixed = new MockHttpServletRequest(
                J7ImportHttpContract.METHOD,
                "/receiver" + J7ImportHttpContract.PATH);
        servletPrefixed.setServletPath("/receiver");
        servletPrefixed.setPathInfo(J7ImportHttpContract.PATH);
        servletPrefixed.addHeader(J7ImportHttpContract.CONTENT_LENGTH, "1");
        assertRejected(servletPrefixed, 400);
    }

    private MockHttpServletRequest requestWithLength(String length) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                J7ImportHttpContract.METHOD, J7ImportHttpContract.PATH);
        request.addHeader(J7ImportHttpContract.CONTENT_LENGTH, length);
        return request;
    }

    private MockHttpServletResponse assertRejected(
            MockHttpServletRequest request,
            int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).doesNotContain("payload", "Idempotency-Key");
        return response;
    }
}
