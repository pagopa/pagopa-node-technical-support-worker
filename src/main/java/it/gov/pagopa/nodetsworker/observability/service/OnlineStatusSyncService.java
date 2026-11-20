package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.exception.OnlineSourceStateException;
import it.gov.pagopa.nodetsworker.observability.ingestion.EventTime;
import it.gov.pagopa.nodetsworker.observability.ingestion.IngestionProjectionWriter;
import it.gov.pagopa.nodetsworker.observability.model.AttemptState;
import it.gov.pagopa.nodetsworker.observability.model.LogicalState;
import it.gov.pagopa.nodetsworker.observability.repository.OnlineStatusRepository;
import it.gov.pagopa.nodetsworker.observability.repository.OnlineStatusSnapshot;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;

/** Reads authoritative Nodo Online snapshots and delegates atomic corrections to the projection writer. */
@ApplicationScoped
public class OnlineStatusSyncService {

  @Inject
  OnlineStatusRepository onlineRepository;

  @Inject
  ProjectionRepository projectionRepository;

  @Inject
  IngestionProjectionWriter writer;

  @Inject
  ObservabilityConfig config;

  public record SyncResult(boolean changed, String state) {
  }

  public SyncResult syncPayment(String paFiscalCode, String noticeNumber) throws SQLException {
    var snapshot = onlineRepository.findPaymentStatus(paFiscalCode, noticeNumber);
    if (snapshot.isEmpty()) {
      return new SyncResult(false, null);
    }

    OnlineStatusSnapshot source = snapshot.orElseThrow();
    if (!LogicalState.isValid(source.state())) {
      throw new OnlineSourceStateException("logical payment");
    }
    boolean changed = writer.reconcileLogical(paFiscalCode, noticeNumber, source.state(),
        sourceInstant(source));
    return new SyncResult(changed, source.state());
  }

  public SyncResult syncAttempt(String paFiscalCode, String noticeNumber, String paymentToken)
      throws SQLException {
    var snapshot = onlineRepository.findAttemptStatus(paFiscalCode, noticeNumber, paymentToken);
    if (snapshot.isEmpty()) {
      return new SyncResult(false, null);
    }

    OnlineStatusSnapshot source = snapshot.orElseThrow();
    if (!AttemptState.isValid(source.state())) {
      throw new OnlineSourceStateException("payment attempt");
    }

    String logicalState = null;
    Instant logicalInstant = null;
    if (!projectionRepository.existsPayment(paFiscalCode, noticeNumber)) {
      var logicalSnapshot = onlineRepository.findPaymentStatus(paFiscalCode, noticeNumber);
      if (logicalSnapshot.isPresent()) {
        OnlineStatusSnapshot logicalSource = logicalSnapshot.orElseThrow();
        if (!LogicalState.isValid(logicalSource.state())) {
          throw new OnlineSourceStateException("logical payment");
        }
        logicalState = logicalSource.state();
        logicalInstant = sourceInstant(logicalSource);
      }
    }

    boolean changed = writer.reconcileAttempt(paFiscalCode, noticeNumber, paymentToken, source.state(),
        sourceInstant(source), logicalState, logicalInstant, config.retentionDays());
    return new SyncResult(changed, source.state());
  }

  private static Instant sourceInstant(OnlineStatusSnapshot snapshot) {
    LocalDateTime timestamp = snapshot.updatedTimestamp() != null
        ? snapshot.updatedTimestamp() : snapshot.insertedTimestamp();
    return EventTime.toInstant(timestamp);
  }
}
