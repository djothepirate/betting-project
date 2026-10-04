package com.bettingproject.collection.application.enrichment;

import java.util.Objects;
import com.bettingproject.collection.domain.budget.BudgetModel.ResultCode;

public record EnrichmentPreparation(ResultCode budgetCode, EnrichmentCollectionAttempt attempt,
        boolean newlyPrepared) {
    public EnrichmentPreparation {
        Objects.requireNonNull(budgetCode);
        if ((budgetCode == ResultCode.OK) != (attempt != null)) {
            throw new IllegalArgumentException("preparation result is inconsistent");
        }
    }
}
