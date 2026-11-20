package it.gov.pagopa.nodetsworker.observability.service;

import io.quarkus.runtime.StartupEvent;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.Fixtures;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Seeds the synthetic demo projection at startup when {@code observability.seed.enabled} is true
 * (local/demo only) and the projection is empty. Never runs in production.
 */
@ApplicationScoped
public class ObservabilitySeeder {

  private static final Logger LOG = Logger.getLogger(ObservabilitySeeder.class);

  @ConfigProperty(name = "observability.seed.enabled", defaultValue = "false")
  boolean seedEnabled;

  @Inject
  ProjectionRepository repository;

  void onStart(@Observes StartupEvent event) {
    if (!seedEnabled) {
      return;
    }
    try {
      if (repository.countPayments() > 0) {
        return;
      }
      for (DomainPayment payment : Fixtures.build(Instant.now())) {
        repository.persist(ProjectionMapper.toEntity(payment));
      }
      repository.meta();
      LOG.info("Observability demo projection seeded with synthetic fixtures");
    } catch (RuntimeException e) {
      LOG.warn("Observability demo projection seeding skipped", e);
    }
  }
}
