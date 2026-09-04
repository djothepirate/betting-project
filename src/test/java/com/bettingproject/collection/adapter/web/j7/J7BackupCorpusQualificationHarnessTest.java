package com.bettingproject.collection.adapter.web.j7;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7BackupCorpusQualificationHarnessTest {

    private static final String DATABASE = "int001_j7_source_20260904";
    private static final String USERNAME = "betting";
    private static final String PASSWORD = "test-only";

    @Test
    void acceptsOnlyTheExactLoopbackQualificationBoundary() {
        J7BackupCorpusQualificationHarness.QualificationTarget target =
                J7BackupCorpusQualificationHarness.QualificationTarget.from(
                        properties(), environment());

        assertThat(target.jdbcUrl())
                .isEqualTo("jdbc:postgresql://127.0.0.1:5433/" + DATABASE);
        assertThat(target.database()).isEqualTo(DATABASE);
        assertThat(target.username()).isEqualTo(USERNAME);
        assertThat(target.password()).isEqualTo(PASSWORD);
        assertThat(target.toString())
                .contains("password=<redacted>")
                .doesNotContain(PASSWORD);
    }

    @Test
    void rejectsMissingOrIncorrectActionConfirmation() {
        Map<String, String> missing = new HashMap<>(properties());
        missing.remove(J7BackupCorpusQualificationHarness.ACTION_PROPERTY);
        assertThatThrownBy(() -> target(missing, environment()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INT001_CORPUS_PROPERTY_MISSING:"
                        + J7BackupCorpusQualificationHarness.ACTION_PROPERTY);

        Map<String, String> incorrect = new HashMap<>(properties());
        incorrect.put(J7BackupCorpusQualificationHarness.ACTION_PROPERTY, "true");
        assertThatThrownBy(() -> target(incorrect, environment()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INT001_CORPUS_ACTION_NOT_CONFIRMED");
    }

    @Test
    void rejectsUnknownQualificationProperties() {
        Map<String, String> properties = new HashMap<>(properties());
        properties.put("int001.backup.corpus.host", "example.test");

        assertThatThrownBy(() -> target(properties, environment()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INT001_CORPUS_UNKNOWN_PROPERTY:int001.backup.corpus.host");
    }

    @Test
    void rejectsDatabaseNamesOutsideTheDedicatedSyntheticNamespace() {
        for (String database : new String[]{
                "betting", "int001_j7_restore_20260904", "int001_j7_source_BAD", ""}) {
            Map<String, String> properties = new HashMap<>(properties());
            properties.put(J7BackupCorpusQualificationHarness.DATABASE_PROPERTY, database);

            assertThatThrownBy(() -> target(properties, environment()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(database.isEmpty()
                            ? "INT001_CORPUS_PROPERTY_MISSING:"
                                    + J7BackupCorpusQualificationHarness.DATABASE_PROPERTY
                            : "INT001_CORPUS_DATABASE_INVALID");
        }
    }

    @Test
    void rejectsMissingInvalidOrPotentiallyLoggedPasswords() {
        for (String password : new String[]{null, "", "line1\nline2", "nul\0value"}) {
            Map<String, String> environment = new HashMap<>(environment());
            if (password == null) {
                environment.remove(J7BackupCorpusQualificationHarness.PASSWORD_ENVIRONMENT);
            }
            else {
                environment.put(
                        J7BackupCorpusQualificationHarness.PASSWORD_ENVIRONMENT, password);
            }

            assertThatThrownBy(() -> target(properties(), environment))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("INT001_CORPUS_PASSWORD_ENVIRONMENT_INVALID");
        }
    }

    @Test
    void rejectsAmbientLibpqConnectionSelectors() {
        for (String selector : new String[]{
                "PGPASSWORD", "PGHOSTADDR", "PGSERVICE", "PGSERVICEFILE",
                "PGHOST", "PGPORT", "PGDATABASE", "PGUSER"}) {
            Map<String, String> environment = new HashMap<>(environment());
            environment.put(selector, "synthetic-value");

            assertThatThrownBy(() -> target(properties(), environment))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("INT001_CORPUS_AMBIENT_POSTGRES_SELECTOR_REJECTED:"
                            + selector);
        }
    }

    private J7BackupCorpusQualificationHarness.QualificationTarget target(
            Map<String, String> properties,
            Map<String, String> environment) {
        return J7BackupCorpusQualificationHarness.QualificationTarget.from(
                properties, environment);
    }

    private Map<String, String> properties() {
        return Map.of(
                J7BackupCorpusQualificationHarness.ACTION_PROPERTY,
                J7BackupCorpusQualificationHarness.REQUIRED_ACTION,
                J7BackupCorpusQualificationHarness.DATABASE_PROPERTY,
                DATABASE,
                J7BackupCorpusQualificationHarness.USERNAME_PROPERTY,
                USERNAME);
    }

    private Map<String, String> environment() {
        return Map.of(J7BackupCorpusQualificationHarness.PASSWORD_ENVIRONMENT, PASSWORD);
    }
}
