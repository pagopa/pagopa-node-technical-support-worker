package it.gov.pagopa.nodetsworker.observability.ingestion;

import io.quarkus.scheduler.Scheduled;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.jboss.logging.Logger;

/**
 * Enforces the 60-day retention (spec §1, §4.4). Each run removes attempts (and their timelines) past
 * {@code expiresAt} ({@code attemptStartedAt + 60d}, or the first technical processing time when the
 * start is absent), the logical payments left with no attempt, and the expired technical inbox/anomaly
 * rows plus forwarded outbox rows older than the retention window. Disabled in test.
 */
@ApplicationScoped
public class RetentionCleaner {

  private static final Logger LOG = Logger.getLogger(RetentionCleaner.class);

  @Inject
  ProjectionRepository repository;

  @Inject
  ObservabilityConfig config;

  @Scheduled(cron = "{observability.retention.cleanup-cron}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void sweep() {
    try {
      Instant now = Instant.now();
      Instant outboxCutoff = now.minus(config.retentionDays(), ChronoUnit.DAYS);
      int removed = repository.sweepExpired(now, outboxCutoff);
      if (removed > 0) {
        LOG.infof("Retention sweep removed %d expired attempt(s)", removed);
      }
    } catch (RuntimeException e) {
      LOG.warn("Retention sweep skipped", e);
    }
  }
}
