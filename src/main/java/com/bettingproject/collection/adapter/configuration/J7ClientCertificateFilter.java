package com.bettingproject.collection.adapter.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Date;
import java.util.HexFormat;
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

import com.bettingproject.collection.adapter.web.j7.J7ImportHttpContract;

@Component
@Profile("control-api")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(
        prefix = "betting.integration.j7-receiver",
        name = "enabled",
        havingValue = "true")
public class J7ClientCertificateFilter extends OncePerRequestFilter {

    static final String RECEIVER_PATH = J7ImportHttpContract.PATH;
    public static final String CLIENT_CERTIFICATE_SHA256_ATTRIBUTE =
            J7ClientCertificateFilter.class.getName() + ".clientCertificateSha256";
    private static final String CERTIFICATE_ATTRIBUTE =
            "jakarta.servlet.request.X509Certificate";
    private static final String CLIENT_AUTHENTICATION_EKU = "1.3.6.1.5.5.7.3.2";
    private static final byte[] CERTIFICATE_REFUSED = (
            "{\"type\":\"urn:betting-project:problem:J7_CLIENT_CERTIFICATE_REFUSED\","+
            "\"title\":\"J7 sender identity refused\",\"status\":403,"+
            "\"detail\":\"The authenticated client is not authorized for this receiver.\","+
            "\"code\":\"J7_CLIENT_CERTIFICATE_REFUSED\","+
            "\"instance\":\"" + RECEIVER_PATH + "\"}")
            .getBytes(StandardCharsets.UTF_8);

    private final List<byte[]> allowedFingerprints;
    private final Clock clock;

    public J7ClientCertificateFilter(J7ReceiverProperties properties, Clock clock) {
        this.allowedFingerprints = properties.getClientCertificateSha256Allowlist().stream()
                .map(HexFormat.of()::parseHex)
                .map(byte[]::clone)
                .toList();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !J7ImportHttpContract.belongsToReceiverRouteFamily(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        X509Certificate certificate = leafCertificate(request);
        byte[] fingerprint = certificate == null ? null : fingerprint(certificate);
        if (certificate == null
                || !isCurrentlyValid(certificate)
                || !hasClientAuthenticationExtendedKeyUsage(certificate)
                || fingerprint == null
                || !isAllowed(fingerprint)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/problem+json");
            response.setHeader("Cache-Control", "no-store");
            response.setContentLength(CERTIFICATE_REFUSED.length);
            response.getOutputStream().write(CERTIFICATE_REFUSED);
            return;
        }
        request.setAttribute(
                CLIENT_CERTIFICATE_SHA256_ATTRIBUTE,
                HexFormat.of().formatHex(fingerprint));
        filterChain.doFilter(request, response);
    }

    private X509Certificate leafCertificate(HttpServletRequest request) {
        Object attribute = request.getAttribute(CERTIFICATE_ATTRIBUTE);
        if (!(attribute instanceof X509Certificate[] chain) || chain.length == 0) {
            return null;
        }
        return chain[0];
    }

    private boolean isCurrentlyValid(X509Certificate certificate) {
        try {
            certificate.checkValidity(Date.from(clock.instant()));
            return true;
        }
        catch (CertificateExpiredException | CertificateNotYetValidException exception) {
            return false;
        }
    }

    private byte[] fingerprint(X509Certificate certificate) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        catch (CertificateEncodingException exception) {
            return null;
        }
    }

    private boolean hasClientAuthenticationExtendedKeyUsage(X509Certificate certificate) {
        try {
            List<String> extendedKeyUsage = certificate.getExtendedKeyUsage();
            return extendedKeyUsage != null
                    && extendedKeyUsage.contains(CLIENT_AUTHENTICATION_EKU);
        }
        catch (CertificateParsingException exception) {
            return false;
        }
    }

    private boolean isAllowed(byte[] actual) {
        return allowedFingerprints.stream()
                .anyMatch(allowed -> MessageDigest.isEqual(allowed, actual));
    }
}
