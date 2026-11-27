package it.gov.pagopa.nodetsworker.observability.ingestion;

import it.gov.pagopa.nodetsworker.datasource.ObservabilityDatasourceDisabled;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import org.jboss.logging.Logger;

/** Periodically flags primitive requests that have not been answered within the response budget. */
@ApplicationScoped
public class PrimitiveTimeoutSweeper {

  private static final Logger LOG = Logger.getLogger(PrimitiveTimeoutSweeper.class);

  @Inject
  PrimitiveIngestionService service;

  @Scheduled(every = "{observability.primitives.sweep-interval:2s}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
      skipExecutionIf = ObservabilityDatasourceDisabled.class)
  void sweep() {
    try {
      service.sweep(Instant.now());
    } catch (RuntimeException e) {
      LOG.warn("Primitive timeout sweep skipped", e);
    }
  }
}
