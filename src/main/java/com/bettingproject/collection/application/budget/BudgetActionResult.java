package com.bettingproject.collection.application.budget;

import java.util.UUID;
import java.time.Instant;

import com.bettingproject.collection.domain.budget.BudgetModel.Intent;
import com.bettingproject.collection.domain.budget.BudgetModel.ResultCode;

public record BudgetActionResult(ResultCode code, UUID windowId, Intent intent, boolean created, Instant retryAt) {
    public BudgetActionResult(ResultCode code, UUID windowId, Intent intent, boolean created) {
        this(code, windowId, intent, created, null);
    }

    public static BudgetActionResult refused(ResultCode code, UUID windowId) {
        return new BudgetActionResult(code, windowId, null, false);
    }
}
