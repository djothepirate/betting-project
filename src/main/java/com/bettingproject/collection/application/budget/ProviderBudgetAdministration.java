package com.bettingproject.collection.application.budget;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Administrative entry point; no HTTP adapter is introduced in this lot. */
@Service
@Profile("control-api")
@Transactional(propagation = Propagation.NEVER)
public class ProviderBudgetAdministration {
    private final ProviderBudgetAdministrationTransactions transactions;

    public ProviderBudgetAdministration(ProviderBudgetAdministrationTransactions transactions) {
        this.transactions = transactions;
    }

    public BudgetActionResult initialize(BudgetCommands.Initialize command) {
        return ProviderBudgetService.validated(() -> transactions.initialize(command));
    }

    public BudgetActionResult reconcile(BudgetCommands.Reconcile command) {
        return ProviderBudgetService.validated(() -> transactions.reconcile(command));
    }

    public BudgetActionResult reconcileQuota(BudgetCommands.ReconcileQuota command) {
        return ProviderBudgetService.validated(() -> transactions.reconcileQuota(command));
    }
}
