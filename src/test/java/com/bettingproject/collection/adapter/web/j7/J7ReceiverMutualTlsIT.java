package com.bettingproject.collection.adapter.web.j7;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.bettingproject.bootstrap.NoDatabaseTestConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                "BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER",
                "betting.integration.j7-receiver.enabled=true",
                "server.address=127.0.0.1",
                "server.port=8444",
                "server.ssl.enabled=true",
                "server.ssl.client-auth=NEED",
                "server.ssl.key-store-type=PKCS12",
                "server.ssl.trust-store-type=PKCS12"
        })
@ActiveProfiles("control-api")
@Import(NoDatabaseTestConfiguration.class)
class J7ReceiverMutualTlsIT {

    private static final String PASSWORD = "TEST_ONLY_PLACEHOLDER";
    private static final EphemeralPki PKI = EphemeralPki.create();
    private static final URI INFO = URI.create("https://127.0.0.1:8444/actuator/info");
    private static final URI RECEIVER = URI.create(
            "https://127.0.0.1:8444" + J7ImportHttpContract.PATH);

    @DynamicPropertySource
    static void tlsProperties(DynamicPropertyRegistry registry) {
        registry.add("server.ssl.key-store", () -> PKI.serverStore().toUri().toString());
        registry.add("server.ssl.key-store-password", () -> PASSWORD);
        registry.add("server.ssl.trust-store", () -> PKI.serverTrustStore().toUri().toString());
        registry.add("server.ssl.trust-store-password", () -> PASSWORD);
        registry.add(
                "betting.integration.j7-receiver.client-certificate-sha256-allowlist",
                PKI::allowedClientSha256);
    }

    @AfterAll
    static void removeEphemeralPki() throws IOException {
        PKI.close();
    }

    @Test
    void enforcesTlsChainAndExactClientFingerprintBeforeController() throws Exception {
        HttpClient allowed = client(PKI.allowedClientStore());
        HttpResponse<String> info = allowed.send(
                HttpRequest.newBuilder(INFO).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(info.statusCode()).isEqualTo(200);

        HttpResponse<String> allowedReceiver = allowed.send(
                receiverRequest(), HttpResponse.BodyHandlers.ofString());
        assertThat(allowedReceiver.statusCode()).isEqualTo(400);
        assertThat(allowedReceiver.body()).contains("MISSING_HEADER");

        HttpResponse<String> trustedButNotAllowlisted = client(PKI.otherClientStore()).send(
                receiverRequest(), HttpResponse.BodyHandlers.ofString());
        assertThat(trustedButNotAllowlisted.statusCode()).isEqualTo(403);
        assertThat(trustedButNotAllowlisted.headers()
                .firstValue("Content-Type").orElseThrow())
                .startsWith("application/problem+json");
        assertThat(trustedButNotAllowlisted.body())
                .contains("J7_CLIENT_CERTIFICATE_REFUSED")
                .doesNotContain(PKI.allowedClientSha256());

        assertThatThrownBy(() -> client(null).send(
                HttpRequest.newBuilder(INFO).GET().build(),
                HttpResponse.BodyHandlers.discarding()))
                .isInstanceOf(IOException.class);

        assertThatThrownBy(() -> client(PKI.untrustedClientStore()).send(
                HttpRequest.newBuilder(INFO).GET().build(),
                HttpResponse.BodyHandlers.discarding()))
                .isInstanceOf(IOException.class);

        HttpResponse<String> wrongExtendedKeyUsage = client(
                PKI.wrongExtendedKeyUsageClientStore()).send(
                        receiverRequest(), HttpResponse.BodyHandlers.ofString());
        assertThat(wrongExtendedKeyUsage.statusCode()).isEqualTo(403);

        HttpResponse<String> expired = client(PKI.expiredClientStore()).send(
                receiverRequest(), HttpResponse.BodyHandlers.ofString());
        assertThat(expired.statusCode()).isEqualTo(403);
    }

    private HttpRequest receiverRequest() {
        return HttpRequest.newBuilder(RECEIVER)
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[] {'{', '}'}))
                .build();
    }

    private HttpClient client(Path identityStore) throws Exception {
        KeyManagerFactory keyManagers = null;
        if (identityStore != null) {
            KeyStore identities = loadStore(identityStore);
            keyManagers = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(identities, PASSWORD.toCharArray());
        }
        KeyStore trustedServer = loadStore(PKI.clientTrustStore());
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustedServer);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(
                keyManagers == null ? null : keyManagers.getKeyManagers(),
                trustManagers.getTrustManagers(),
                null);
        return HttpClient.newBuilder()
                .sslContext(context)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private KeyStore loadStore(Path path) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(path)) {
            store.load(input, PASSWORD.toCharArray());
        }
        return store;
    }

    private record EphemeralPki(
            Path root,
            Path serverStore,
            Path serverTrustStore,
            Path clientTrustStore,
            Path allowedClientStore,
            Path otherClientStore,
            Path untrustedClientStore,
            Path wrongExtendedKeyUsageClientStore,
            Path expiredClientStore,
            String allowedClientSha256) implements AutoCloseable {

        static EphemeralPki create() {
            try {
                Path root = Files.createTempDirectory("int001-mtls-").toRealPath();
                Path server = root.resolve("server.p12");
                Path allowed = root.resolve("allowed-client.p12");
                Path other = root.resolve("other-client.p12");
                Path untrusted = root.resolve("untrusted-client.p12");
                Path wrongExtendedKeyUsage = root.resolve("wrong-eku-client.p12");
                Path expired = root.resolve("expired-client.p12");
                Path serverCertificate = root.resolve("server.cer");
                Path allowedCertificate = root.resolve("allowed-client.cer");
                Path otherCertificate = root.resolve("other-client.cer");
                Path wrongExtendedKeyUsageCertificate = root.resolve("wrong-eku-client.cer");
                Path expiredCertificate = root.resolve("expired-client.cer");
                Path serverTrust = root.resolve("server-trust.p12");
                Path clientTrust = root.resolve("client-trust.p12");

                generateIdentity(server, "server", "CN=int001-server", "SAN=ip:127.0.0.1", "serverAuth");
                generateIdentity(allowed, "allowed", "CN=int001-allowed", null, "clientAuth");
                generateIdentity(other, "other", "CN=int001-other", null, "clientAuth");
                generateIdentity(untrusted, "untrusted", "CN=int001-untrusted", null, "clientAuth");
                generateIdentity(
                        wrongExtendedKeyUsage,
                        "wrong-eku",
                        "CN=int001-wrong-eku",
                        null,
                        "serverAuth");
                generateIdentity(
                        expired,
                        "expired",
                        "CN=int001-expired",
                        null,
                        "clientAuth",
                        "-2d",
                        "1");
                exportCertificate(server, "server", serverCertificate);
                exportCertificate(allowed, "allowed", allowedCertificate);
                exportCertificate(other, "other", otherCertificate);
                exportCertificate(
                        wrongExtendedKeyUsage,
                        "wrong-eku",
                        wrongExtendedKeyUsageCertificate);
                exportCertificate(expired, "expired", expiredCertificate);
                importCertificate(serverTrust, "allowed", allowedCertificate);
                importCertificate(serverTrust, "other", otherCertificate);
                importCertificate(serverTrust, "wrong-eku", wrongExtendedKeyUsageCertificate);
                importCertificate(serverTrust, "expired", expiredCertificate);
                importCertificate(clientTrust, "server", serverCertificate);

                return new EphemeralPki(
                        root,
                        server,
                        serverTrust,
                        clientTrust,
                        allowed,
                        other,
                        untrusted,
                        wrongExtendedKeyUsage,
                        expired,
                        certificateSha256(allowedCertificate));
            }
            catch (Exception exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static void generateIdentity(
                Path store,
                String alias,
                String distinguishedName,
                String subjectAlternativeName,
                String extendedKeyUsage) throws Exception {
            generateIdentity(
                    store,
                    alias,
                    distinguishedName,
                    subjectAlternativeName,
                    extendedKeyUsage,
                    null,
                    "2");
        }

        private static void generateIdentity(
                Path store,
                String alias,
                String distinguishedName,
                String subjectAlternativeName,
                String extendedKeyUsage,
                String startDate,
                String validityDays) throws Exception {
            java.util.List<String> arguments = new java.util.ArrayList<>(java.util.List.of(
                    "-genkeypair",
                    "-alias", alias,
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", validityDays,
                    "-dname", distinguishedName,
                    "-ext", "EKU=" + extendedKeyUsage,
                    "-storetype", "PKCS12",
                    "-keystore", store.toString(),
                    "-storepass", PASSWORD,
                    "-keypass", PASSWORD,
                    "-noprompt"));
            if (startDate != null) {
                arguments.add(arguments.indexOf("-dname"), "-startdate");
                arguments.add(arguments.indexOf("-dname"), startDate);
            }
            if (subjectAlternativeName != null) {
                arguments.add(arguments.indexOf("-storetype"), "-ext");
                arguments.add(arguments.indexOf("-storetype"), subjectAlternativeName);
            }
            keytool(arguments);
        }

        private static void exportCertificate(Path store, String alias, Path output)
                throws Exception {
            keytool(java.util.List.of(
                    "-exportcert", "-alias", alias,
                    "-keystore", store.toString(),
                    "-storepass", PASSWORD,
                    "-file", output.toString()));
        }

        private static void importCertificate(Path store, String alias, Path certificate)
                throws Exception {
            keytool(java.util.List.of(
                    "-importcert", "-alias", alias,
                    "-keystore", store.toString(),
                    "-storetype", "PKCS12",
                    "-storepass", PASSWORD,
                    "-file", certificate.toString(),
                    "-noprompt"));
        }

        private static void keytool(java.util.List<String> arguments) throws Exception {
            Path binary = Path.of(
                    System.getProperty("java.home"),
                    "bin",
                    System.getProperty("os.name").startsWith("Windows")
                            ? "keytool.exe"
                            : "keytool");
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add(binary.toString());
            command.addAll(arguments);
            Process process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("ephemeral keytool command timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("ephemeral keytool command failed");
            }
        }

        private static String certificateSha256(Path certificatePath) throws Exception {
            try (InputStream input = Files.newInputStream(certificatePath)) {
                X509Certificate certificate = (X509Certificate) CertificateFactory
                        .getInstance("X.509").generateCertificate(input);
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(certificate.getEncoded()));
            }
        }

        @Override
        public void close() throws IOException {
            if (!Files.exists(root)) {
                return;
            }
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
