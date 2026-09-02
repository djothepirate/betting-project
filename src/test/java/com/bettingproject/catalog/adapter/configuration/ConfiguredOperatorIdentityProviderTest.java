package com.bettingproject.catalog.adapter.configuration;

import com.bettingproject.catalog.application.OperatorIdentityResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredOperatorIdentityProviderTest {

    @Test
    void returnsTheTrimmedConfiguredIdentity() {
        ConfiguredOperatorIdentityProvider provider =
                new ConfiguredOperatorIdentityProvider("  test-operator  ");

        assertThat(provider.currentOperator())
                .isInstanceOfSatisfying(OperatorIdentityResult.Available.class, available ->
                        assertThat(available.identity().value()).isEqualTo("test-operator"));
    }

    @Test
    void treatsMissingOrInvalidConfigurationAsUnavailable() {
        assertThat(new ConfiguredOperatorIdentityProvider("").currentOperator())
                .isInstanceOf(OperatorIdentityResult.Unavailable.class);
        assertThat(new ConfiguredOperatorIdentityProvider("invalid\noperator").currentOperator())
                .isInstanceOf(OperatorIdentityResult.Unavailable.class);
    }
}
