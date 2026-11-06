package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.observability.domain.DbCheckContext;
import it.gov.pagopa.nodetsworker.observability.dto.DbCheckDto;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import it.gov.pagopa.nodetsworker.observability.model.AttemptState;
import it.gov.pagopa.nodetsworker.observability.model.LogicalState;
import it.gov.pagopa.nodetsworker.observability.repository.OnlineDbCheckRepository;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import it.gov.pagopa.nodetsworker.service.NodeSnapshotRouter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import javax.sql.DataSource;
import org.jboss.logging.Logger;

/** Compares captured projection state with Online without calling any projection writer. */
@ApplicationScoped
public class DbCheckService {
  private static final Logger LOG = Logger.getLogger(DbCheckService.class);
  @Inject ProjectionRepository projection;
  @Inject NodeSnapshotRouter router;
  @Inject OnlineDbCheckRepository online;

  public DbCheckDto check(Caller caller, String attemptId) {
    if (attemptId == null || attemptId.isBlank() || attemptId.length() > 64) {
      throw Errors.notFound("Tentativo non disponibile.");
    }
    DbCheckContext context = context(caller, attemptId);
    if (context.serviceIdentifier() == null || context.serviceIdentifier().isBlank()) {
      throw new ObservabilityException(422, "DB_SERVICE_MISSING",
          "serviceIdentifier non disponibile per il tentativo.", null, null);
    }
    DataSource source;
    try {
      source = router.resolveDataSource(context.serviceIdentifier());
    } catch (AppException failure) {
      throw new ObservabilityException(422, "DB_SERVICE_UNCONFIGURED",
          "Nessun database configurato per il servizio del tentativo.", null, null);
    }
    if (context.paymentToken() == null || context.paymentToken().isBlank()
        || !LogicalState.isValid(context.logicalState()) || !AttemptState.isValid(context.attemptState())) {
      LOG.error("Check DB cannot compare an incomplete projection context.");
      throw Errors.internal();
    }
    OnlineDbCheckRepository.States states;
    try {
      states = online.read(source, context.paFiscalCode(), context.noticeNumber(), context.paymentToken());
    } catch (OnlineDbCheckRepository.InvalidSnapshotException failure) {
      LOG.error("Check DB rejected an invalid or ambiguous Nodo Online snapshot.");
      throw Errors.internal();
    } catch (SQLException failure) {
      // Driver messages may contain bound payment tokens, SQL or connection details.
      LOG.error("Check DB could not read the Nodo Online database.");
      throw new ObservabilityException(503, "DB_UNAVAILABLE",
          "Database nodo-online non disponibile. Riprovare il controllo.", null, null);
    }
    DbCheckContext retained = context(caller, attemptId);
    if (!Objects.equals(context.paFiscalCode(), retained.paFiscalCode())
        || !Objects.equals(context.noticeNumber(), retained.noticeNumber())
        || !Objects.equals(context.paymentToken(), retained.paymentToken())
        || !Objects.equals(context.serviceIdentifier(), retained.serviceIdentifier())) {
      LOG.error("Check DB projection identity changed during the source read.");
      throw Errors.internal();
    }
    return new DbCheckDto(context.attemptId(), context.paFiscalCode(), context.noticeNumber(),
        context.serviceIdentifier(), context.projectionRevision(), "NODO_ONLINE", IsoTime.format(Instant.now()),
        new DbCheckDto.ObservedStates(LogicalState.valueOf(context.logicalState()),
            AttemptState.valueOf(context.attemptState())),
        new DbCheckDto.DatabaseStates(
            states.logicalState() == null ? null : LogicalState.valueOf(states.logicalState()),
            states.attemptState() == null ? null : AttemptState.valueOf(states.attemptState())));
  }

  private DbCheckContext context(Caller caller, String attemptId) {
    return projection.findDbCheckContext(attemptId, caller.allowedPa(), Instant.now())
        .orElseThrow(() -> Errors.notFound("Tentativo non disponibile o scaduto per retention."));
  }
}
