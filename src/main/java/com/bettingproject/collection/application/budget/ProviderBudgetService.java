package com.bettingproject.collection.application.budget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.bettingproject.collection.domain.budget.BudgetModel.*;

/** No transaction may outlive an operation and accidentally delay an emission permit's commit. */
@Service
@Profile({"control-api", "batch-worker"})
@Transactional(propagation = Propagation.NEVER)
public class ProviderBudgetService {
    private final ProviderBudgetTransactions transactions;

    public ProviderBudgetService(ProviderBudgetTransactions transactions) {
        this.transactions = transactions;
    }

    public BudgetActionResult reserve(BudgetCommands.Reserve command) {
        return validated(() -> transactions.reserve(command));
    }

    public BudgetActionResult authorizeSend(UUID intentId) {
        return validated(() -> transactions.authorizeSend(intentId));
    }

    public BudgetActionResult release(UUID intentId) {
        return validated(() -> transactions.release(intentId));
    }

    public BudgetActionResult markUncertain(UUID intentId) {
        return validated(() -> transactions.markUncertain(intentId));
    }

    public BudgetActionResult recordOutcome(BudgetCommands.Outcome command) {
        return validated(() -> transactions.recordOutcome(command));
    }

    public BudgetActionResult observeQuota(UUID windowId, BudgetCommands.QuotaReading reading) {
        return validated(() -> transactions.observeQuota(windowId, reading));
    }

    public Availability availability(UUID windowId) {
        return transactions.availability(windowId);
    }

    public Optional<Intent> findIntent(UUID intentId) {
        return transactions.findIntent(intentId);
    }

    public List<Incident> incidents(UUID windowId) {
        return transactions.incidents(windowId);
    }

    static BudgetActionResult validated(Supplier<BudgetActionResult> action) {
        try {
            return action.get();
        }
        catch (IllegalArgumentException | NullPointerException exception) {
            return BudgetActionResult.refused(ResultCode.INVALID_COMMAND, null);
        }
    }
}
