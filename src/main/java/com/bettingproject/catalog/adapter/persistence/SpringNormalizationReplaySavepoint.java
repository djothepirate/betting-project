package com.bettingproject.catalog.adapter.persistence;

import com.bettingproject.catalog.application.NormalizationReplaySavepoint;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Component
@Profile("control-api")
public class SpringNormalizationReplaySavepoint implements NormalizationReplaySavepoint {

    @Override
    public Object create() {
        return TransactionAspectSupport.currentTransactionStatus().createSavepoint();
    }

    @Override
    public void rollback(Object savepoint) {
        TransactionAspectSupport.currentTransactionStatus().rollbackToSavepoint(savepoint);
    }

    @Override
    public void release(Object savepoint) {
        TransactionAspectSupport.currentTransactionStatus().releaseSavepoint(savepoint);
    }
}
