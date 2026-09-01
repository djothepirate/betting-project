package com.bettingproject.collection.adapter.replay;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Command-line entry point intended for Spring Boot {@code PropertiesLauncher}. */
public final class EnrEvidenceCorpusVerifierCli {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("evidence-root", "index");

    private EnrEvidenceCorpusVerifierCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        return run(args, out, err, objectMapper, new EnrEvidenceCorpusVerifier(objectMapper));
    }

    static int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            ObjectMapper objectMapper,
            EnrEvidenceCorpusVerifier verifier) {
        try {
            Map<String, String> options = parseArguments(args);
            var summary = verifier.verify(
                    Path.of(required(options, "evidence-root")),
                    Path.of(required(options, "index")));
            out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
            return summary.passed() ? 0 : 1;
        }
        catch (Exception exception) {
            err.println("ENR-001 corpus verification could not run: " + safeMessage(exception));
            return 2;
        }
    }

    private static Map<String, String> parseArguments(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Arguments must be supplied as --name value pairs");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            String rawName = args[index];
            if (!rawName.startsWith("--")) {
                throw new IllegalArgumentException("Argument names must start with --");
            }
            String name = rawName.substring(2);
            if (!ALLOWED_ARGUMENTS.contains(name) || result.put(name, args[index + 1]) != null) {
                throw new IllegalArgumentException("Unknown or duplicate argument: --" + name);
            }
        }
        return result;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: --" + name);
        }
        return value;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
