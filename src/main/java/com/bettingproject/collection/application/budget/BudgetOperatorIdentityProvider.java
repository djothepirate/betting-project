package com.bettingproject.collection.application.budget;

import java.util.Optional;

public interface BudgetOperatorIdentityProvider {
    Optional<String> currentOperator();
}
