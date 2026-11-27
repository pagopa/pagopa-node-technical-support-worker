package it.gov.pagopa.nodetsworker.datasource;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Skips observability jobs while {@code quarkus.datasource.observability.active=false}. */
@Singleton
public class ObservabilityDatasourceDisabled implements Scheduled.SkipPredicate {

    @Inject
    DatasourceRegistry registry;

    @Override
    public boolean test(ScheduledExecution execution) {
        return !registry.isEnabled(DatasourceRegistry.OBSERVABILITY);
    }
}
