package com.bettingproject.collection.adapter.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredBudgetOperatorIdentityProviderTest {
    @Test
    void returnsOnlyTheTrimmedConfiguredIdentity() {
        assertThat(new ConfiguredBudgetOperatorIdentityProvider("  synthetic-operator  ").currentOperator())
                .contains("synthetic-operator");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "synthetic\noperator", "synthetic\roperator", "synthetic\u0000operator"})
    void missingOrControlBearingIdentityIsUnavailableWithoutPreventingStartup(String configured) {
        assertThat(new ConfiguredBudgetOperatorIdentityProvider(configured).currentOperator()).isEmpty();
    }

    @Test
    void boundsIdentityLengthAfterTrimming() {
        assertThat(new ConfiguredBudgetOperatorIdentityProvider("s").currentOperator()).contains("s");
        assertThat(new ConfiguredBudgetOperatorIdentityProvider(" " + "s".repeat(100) + " ").currentOperator())
                .contains("s".repeat(100));
        assertThat(new ConfiguredBudgetOperatorIdentityProvider("s".repeat(101)).currentOperator()).isEmpty();
    }
}
