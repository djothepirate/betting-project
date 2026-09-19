package com.bettingproject.bootstrap;

import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

final class CollectionControlProfileAssertions {
    static void verify(ApplicationContext context,boolean enabled){
        for(Class<?> type:new Class<?>[]{
            com.bettingproject.collection.application.control.CollectionQueryPort.class,
            com.bettingproject.collection.application.control.CollectionQueryService.class,
            com.bettingproject.collection.application.control.DailySelectionCandidates.class,
            com.bettingproject.collection.application.control.DailySelectionService.class,
            com.bettingproject.collection.adapter.persistence.JdbcCollectionQueryAdapter.class,
            com.bettingproject.catalog.adapter.persistence.JdbcDailySelectionCandidates.class,
            com.bettingproject.operations.application.jobs.JobQueryPort.class,
            com.bettingproject.operations.adapter.persistence.JdbcJobQueryAdapter.class,
            com.bettingproject.collection.adapter.web.control.CollectionControlController.class,
            com.bettingproject.collection.adapter.web.control.CollectionCursorCodec.class,
            com.bettingproject.collection.adapter.web.control.CollectionProblemHandler.class,
            com.bettingproject.collection.adapter.web.control.DailySelectionController.class}){
            assertThat(context.getBeansOfType(type)).as(type.getSimpleName()).hasSize(enabled?1:0);
        }
    }
}
