package com.bettingproject.collection.adapter.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrichmentCollectorCliTest {

    @TempDir
    Path outputRoot;

    @Test
    void refusesAConcurrentCollectorBeforeAnyProviderCall() throws Exception {
        Path lockPath = outputRoot.resolve(".enr-001-collector.lock");
        String[] arguments = {
                "--sample-id", "ENR-P01",
                "--match-id", "1347698848",
                "--endpoint", "detail",
                "--window", "FIRST_AUTHORIZED",
                "--scenarios", "ID-01,TIM-01,OPS-01,REP-01",
                "--output-root", outputRoot.toString(),
                "--manifest", Path.of("docs/benchmark/enrichment-sample-v0.1.json").toString()
        };

        try (FileChannel channel = FileChannel.open(
                        lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock();
                PrintStream out = stream();
                PrintStream err = stream()) {
            assertThatThrownBy(() -> EnrichmentCollectorCli.run(
                            arguments, Map.of("HIGHLIGHTLY_API_KEY", "TEST_ONLY_PLACEHOLDER"), out, err))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already running");
        }
    }

    @Test
    void refusesToResumeAfterAnUncertainProviderCallOutcome() throws Exception {
        Files.createFile(outputRoot.resolve(".enr-001-call-outcome-uncertain"));

        try (PrintStream out = stream(); PrintStream err = stream()) {
            assertThatThrownBy(() -> EnrichmentCollectorCli.run(
                            arguments(outputRoot),
                            Map.of("HIGHLIGHTLY_API_KEY", "TEST_ONLY_PLACEHOLDER"),
                            out,
                            err))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("uncertain outcome");
        }
    }

    @Test
    void refusesAnEvidenceOutputRootInsideTheGitRepository() throws Exception {
        Path forbiddenOutput = Path.of("target", "enr-001-forbidden-output").toAbsolutePath().normalize();

        try (PrintStream out = stream(); PrintStream err = stream()) {
            assertThatThrownBy(() -> EnrichmentCollectorCli.run(
                            arguments(forbiddenOutput),
                            Map.of("HIGHLIGHTLY_API_KEY", "TEST_ONLY_PLACEHOLDER"),
                            out,
                            err))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outside the Git repository");
        }
    }

    private static String[] arguments(Path outputRoot) {
        return new String[] {
                "--sample-id", "ENR-P01",
                "--match-id", "1347698848",
                "--endpoint", "detail",
                "--window", "FIRST_AUTHORIZED",
                "--scenarios", "ID-01,TIM-01,OPS-01,REP-01",
                "--output-root", outputRoot.toString(),
                "--manifest", Path.of("docs/benchmark/enrichment-sample-v0.1.json").toString()
        };
    }

    private static PrintStream stream() {
        return new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
    }
}
