package com.bettingproject.collection.adapter.configuration;

import java.util.Optional;

import com.bettingproject.collection.application.budget.BudgetOperatorIdentityProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("control-api")
public class ConfiguredBudgetOperatorIdentityProvider implements BudgetOperatorIdentityProvider {
    private final String value;

    public ConfiguredBudgetOperatorIdentityProvider(
            @Value("${betting.operator.id:${BETTING_OPERATOR_ID:}}") String configured) {
        this.value = configured == null ? "" : configured.strip();
    }

    @Override
    public Optional<String> currentOperator() {
        return value.isEmpty() || value.length() > 100 || value.codePoints().anyMatch(Character::isISOControl)
                ? Optional.empty() : Optional.of(value);
    }
}
