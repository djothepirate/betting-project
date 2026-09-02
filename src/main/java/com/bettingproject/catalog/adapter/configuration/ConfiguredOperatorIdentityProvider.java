package com.bettingproject.catalog.adapter.configuration;

import java.util.Optional;

import com.bettingproject.catalog.application.OperatorIdentity;
import com.bettingproject.catalog.application.OperatorIdentityProvider;
import com.bettingproject.catalog.application.OperatorIdentityResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("control-api")
public class ConfiguredOperatorIdentityProvider implements OperatorIdentityProvider {

    private final String configuredOperatorId;

    public ConfiguredOperatorIdentityProvider(
            @Value("${betting.operator.id:${BETTING_OPERATOR_ID:}}")
            String configuredOperatorId) {
        this.configuredOperatorId = configuredOperatorId;
    }

    @Override
    public OperatorIdentityResult currentOperator() {
        Optional<OperatorIdentity> identity = OperatorIdentity.fromConfiguredValue(
                configuredOperatorId);
        return identity.<OperatorIdentityResult>map(OperatorIdentityResult.Available::new)
                .orElseGet(OperatorIdentityResult.Unavailable::new);
    }
}
