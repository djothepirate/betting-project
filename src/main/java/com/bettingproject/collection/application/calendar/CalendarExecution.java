package com.bettingproject.collection.application.calendar;

import java.util.UUID;
import java.util.function.Supplier;
import com.bettingproject.collection.application.budget.BudgetActionResult;
import com.bettingproject.collection.application.budget.BudgetCommands;

/** A managed execution fences every durable boundary; a direct call never resumes a running collection. */
public interface CalendarExecution {
    boolean resumable();
    <T> T atomic(Supplier<T> work);
    BudgetActionResult reserve(BudgetCommands.Reserve command);
    BudgetActionResult authorize(UUID intentId);
    BudgetActionResult uncertain(UUID intentId);
}
