package com.bettingproject.collection.application.budget;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bettingproject.collection.domain.budget.BudgetModel.Event;
import com.bettingproject.collection.domain.budget.BudgetModel.Incident;
import com.bettingproject.collection.domain.budget.BudgetModel.Intent;
import com.bettingproject.collection.domain.budget.BudgetModel.QuotaObservation;
import com.bettingproject.collection.domain.budget.BudgetModel.Scope;
import com.bettingproject.collection.domain.budget.BudgetModel.Window;

/** Persistence port. Mutations are called under the scope, window, intention lock order. */
public interface ProviderBudgetRepository {
    Scope createAndLockScope(String provider, String accountRef, Instant now);
    void lockScope(UUID scopeId);
    void lockWindow(UUID windowId);
    void lockIntent(UUID intentId);
    Optional<Window> findWindow(UUID id);
    List<Window> findWindows(UUID scopeId);
    void insertWindow(Window window);
    boolean updateWindow(Window window, long expectedVersion);
    Optional<Intent> findIntent(UUID id);
    Optional<Intent> findIntentByKey(String idempotencyKey);
    Intent insertIntentIfAbsentAndResolve(Intent intent);
    boolean updateIntent(Intent intent, long expectedVersion);
    List<Intent> findWindowIntents(UUID windowId);
    List<Intent> findScopeIntents(UUID scopeId);
    Optional<QuotaObservation> findObservation(UUID id);
    void insertObservation(QuotaObservation observation);
    void insertIncident(Incident incident);
    List<Incident> findIncidents(UUID windowId);
    void appendEvent(Event event);
}
