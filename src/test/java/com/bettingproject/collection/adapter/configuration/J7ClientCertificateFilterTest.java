package com.bettingproject.collection.adapter.configuration;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class J7ClientCertificateFilterTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-02T10:00:00Z"), ZoneOffset.UTC);
    private static final byte[] CERTIFICATE_BYTES = "synthetic-j7-client".getBytes(
            java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    void allowsOnlyTheConfiguredLeafCertificateOnTheReceiverRoute() throws Exception {
        J7ClientCertificateFilter filter = filterFor(CERTIFICATE_BYTES);
        MockHttpServletRequest request = receiverRequest();
        request.setAttribute(
                "jakarta.servlet.request.X509Certificate",
                new X509Certificate[] {new StubCertificate(
                        CERTIFICATE_BYTES,
                        true,
                        "1.3.6.1.5.5.7.3.2")});
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute(
                J7ClientCertificateFilter.CLIENT_CERTIFICATE_SHA256_ATTRIBUTE))
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(CERTIFICATE_BYTES)));
    }

    @Test
    void refusesAbsentUnlistedOrExpiredCertificates() throws Exception {
        J7ClientCertificateFilter filter = filterFor(CERTIFICATE_BYTES);

        MockHttpServletResponse absentResponse = new MockHttpServletResponse();
        filter.doFilter(receiverRequest(), absentResponse, new MockFilterChain());
        assertThat(absentResponse.getStatus()).isEqualTo(403);

        MockHttpServletRequest unlisted = receiverRequest();
        unlisted.setAttribute(
                "jakarta.servlet.request.X509Certificate",
                new X509Certificate[] {new StubCertificate(
                        "other".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        true,
                        "1.3.6.1.5.5.7.3.2")});
        MockHttpServletResponse unlistedResponse = new MockHttpServletResponse();
        filter.doFilter(unlisted, unlistedResponse, new MockFilterChain());
        assertThat(unlistedResponse.getStatus()).isEqualTo(403);
        assertThat(unlistedResponse.getContentType())
                .startsWith("application/problem+json");
        assertThat(unlistedResponse.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(unlistedResponse.getContentAsString())
                .contains("J7_CLIENT_CERTIFICATE_REFUSED")
                .doesNotContain("other", HexFormat.of().formatHex(CERTIFICATE_BYTES));

        MockHttpServletRequest expired = receiverRequest();
        expired.setAttribute(
                "jakarta.servlet.request.X509Certificate",
                new X509Certificate[] {new StubCertificate(
                        CERTIFICATE_BYTES,
                        false,
                        "1.3.6.1.5.5.7.3.2")});
        MockHttpServletResponse expiredResponse = new MockHttpServletResponse();
        filter.doFilter(expired, expiredResponse, new MockFilterChain());
        assertThat(expiredResponse.getStatus()).isEqualTo(403);

        MockHttpServletRequest wrongExtendedKeyUsage = receiverRequest();
        wrongExtendedKeyUsage.setAttribute(
                "jakarta.servlet.request.X509Certificate",
                new X509Certificate[] {new StubCertificate(
                        CERTIFICATE_BYTES,
                        true,
                        "1.3.6.1.5.5.7.3.1")});
        MockHttpServletResponse wrongExtendedKeyUsageResponse =
                new MockHttpServletResponse();
        filter.doFilter(
                wrongExtendedKeyUsage,
                wrongExtendedKeyUsageResponse,
                new MockFilterChain());
        assertThat(wrongExtendedKeyUsageResponse.getStatus()).isEqualTo(403);
    }

    @Test
    void doesNotApplyCertificateIdentityPolicyToOtherLoopbackRoutes() throws Exception {
        J7ClientCertificateFilter filter = filterFor(CERTIFICATE_BYTES);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void appliesCertificatePolicyToMvcNormalizedReceiverAliases() throws Exception {
        J7ClientCertificateFilter filter = filterFor(CERTIFICATE_BYTES);

        for (String uri : List.of(
                "/api;unexpected=true/imports/sofascore/j7-canonical-events",
                "/api/imports;unexpected=true/sofascore/j7-canonical-events",
                "/api/imports/sofascore/%6a7-canonical-events")) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(new MockHttpServletRequest("POST", uri), response, chain);

            assertThat(chain.getRequest()).as(uri).isNull();
            assertThat(response.getStatus()).as(uri).isEqualTo(403);
        }
    }

    private J7ClientCertificateFilter filterFor(byte[] certificateBytes) throws Exception {
        J7ReceiverProperties properties = new J7ReceiverProperties();
        properties.setClientCertificateSha256Allowlist(List.of(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(certificateBytes))));
        return new J7ClientCertificateFilter(properties, CLOCK);
    }

    private MockHttpServletRequest receiverRequest() {
        return new MockHttpServletRequest(
                "POST", J7ClientCertificateFilter.RECEIVER_PATH);
    }

    @SuppressWarnings("deprecation")
    private static final class StubCertificate extends X509Certificate {

        private final byte[] encoded;
        private final boolean valid;
        private final String extendedKeyUsage;

        private StubCertificate(byte[] encoded, boolean valid, String extendedKeyUsage) {
            this.encoded = encoded.clone();
            this.valid = valid;
            this.extendedKeyUsage = extendedKeyUsage;
        }

        @Override
        public void checkValidity() throws java.security.cert.CertificateExpiredException,
                java.security.cert.CertificateNotYetValidException {
            checkValidity(new Date());
        }

        @Override
        public void checkValidity(Date date) throws java.security.cert.CertificateExpiredException,
                java.security.cert.CertificateNotYetValidException {
            if (!valid) {
                throw new java.security.cert.CertificateExpiredException();
            }
        }

        @Override
        public int getVersion() {
            return 3;
        }

        @Override
        public BigInteger getSerialNumber() {
            return BigInteger.ONE;
        }

        @Override
        public Principal getIssuerDN() {
            return () -> "CN=synthetic";
        }

        @Override
        public Principal getSubjectDN() {
            return () -> "CN=synthetic";
        }

        @Override
        public Date getNotBefore() {
            return Date.from(Instant.EPOCH);
        }

        @Override
        public Date getNotAfter() {
            return Date.from(Instant.parse("2099-01-01T00:00:00Z"));
        }

        @Override
        public byte[] getTBSCertificate() {
            return encoded.clone();
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "NONE";
        }

        @Override
        public String getSigAlgOID() {
            return "0.0";
        }

        @Override
        public byte[] getSigAlgParams() {
            return null;
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return null;
        }

        @Override
        public boolean[] getKeyUsage() {
            return null;
        }

        @Override
        public int getBasicConstraints() {
            return -1;
        }

        @Override
        public List<String> getExtendedKeyUsage() {
            return List.of(extendedKeyUsage);
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return encoded.clone();
        }

        @Override
        public void verify(PublicKey key) {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) {
        }

        @Override
        public String toString() {
            return "synthetic-certificate";
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return Set.of();
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return Set.of();
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return null;
        }
    }
}
