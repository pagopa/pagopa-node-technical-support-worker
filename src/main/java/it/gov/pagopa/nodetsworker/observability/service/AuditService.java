package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.repository.models.AuditRecordEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * Persists the audit trail of token exposure. A failed write denies the operation with
 * {@code 500 AUDIT_UNAVAILABLE}; the supplied secret is never recorded.
 */
@ApplicationScoped
public class AuditService {

  private static final Logger LOG = Logger.getLogger(AuditService.class);

  @Inject
  ProjectionRepository repository;

  public void record(Caller caller, String action, String attemptId) {
    try {
      repository.insertAudit(AuditRecordEntity.builder()
          .actorId(caller.id())
          .action(action)
          .attemptId(attemptId)
          .recordedAt(Instant.now())
          .build());
    } catch (RuntimeException e) {
      LOG.error("Audit write failed; denying token operation", e);
      throw Errors.auditUnavailable();
    }
  }
}
