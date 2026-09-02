package com.bettingproject.catalog.application;

import java.util.UUID;

import com.bettingproject.identity.domain.ProviderEntityType;
import com.bettingproject.identity.domain.ProviderMappingKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MappingDecisionCommandTest {

    @Test
    void toStringNeverExposesTheRawJustification() {
        MappingDecisionCommand command = MappingDecisionCommand.confirm(
                new ProviderMappingKey(
                        "synthetic-provider",
                        ProviderEntityType.TEAM,
                        "synthetic-team",
                        "",
                        ""),
                UUID.fromString("00000000-0000-0000-0000-000000000042"),
                0L,
                "mapping-command-string",
                "private operator note fake-placeholder-value");

        assertThat(command.toString())
                .contains("justification=[REDACTED]")
                .doesNotContain("private operator note", "fake-placeholder-value");
    }
}
