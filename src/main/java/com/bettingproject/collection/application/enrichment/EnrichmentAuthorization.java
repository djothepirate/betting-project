package com.bettingproject.collection.application.enrichment;

import java.util.Objects;
import com.bettingproject.collection.domain.budget.BudgetModel.ResultCode;

public record EnrichmentAuthorization(ResultCode code, boolean maySend) {
    public EnrichmentAuthorization {
        Objects.requireNonNull(code);
        if (maySend != (code == ResultCode.OK)) {
            throw new IllegalArgumentException("only a committed OK result can authorize one send");
        }
    }
}
