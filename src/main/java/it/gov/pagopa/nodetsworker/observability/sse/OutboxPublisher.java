package it.gov.pagopa.nodetsworker.observability.sse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.scheduler.Scheduled;
import it.gov.pagopa.nodetsworker.observability.domain.DomainAttempt;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveBucket;
import it.gov.pagopa.nodetsworker.observability.service.FreshnessService;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.repository.models.OutboxEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Drains the outbox and fans out SSE deltas. The outbox is written in the projection write transaction
 * by the Event Hubs consumer (a separate component, out of scope here); this publisher is the delivery
 * half that the contract's SSE semantics require. A batch of pending rows is coalesced into a single
 * publication per the contract ("un solo delta aggregato per scope").
 */
@ApplicationScoped
public class OutboxPublisher {

  private static final Logger LOG = Logger.getLogger(OutboxPublisher.class);
  private static final int BATCH = 200;

  @Inject
  ProjectionRepository repository;

  @Inject
  SseHub hub;

  @Inject
  FreshnessService freshnessService;

  @Inject
  ObjectMapper objectMapper;

  @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void drain() {
    try {
      poll();
    } catch (RuntimeException e) {
      LOG.debug("Outbox drain skipped", e);
    }
  }

  void poll() {
    List<OutboxEntity> rows = repository.unpublishedOutbox(BATCH);
    List<DomainPayment> after = safeSnapshot(repository.loadVisible(null));

    if (rows.isEmpty()) {
      if (hub.lastSnapshot() == null) {
        hub.setLastSnapshot(after);
      }
      return;
    }

    List<DomainPayment> before = hub.lastSnapshot() != null ? hub.lastSnapshot() : after;

    List<HistoryEntry.PaymentChange> paymentChanges = new ArrayList<>();
    List<PrimitiveBucket> primitiveChanges = new ArrayList<>();
    long maxSequence = 0;
    long revision = 0;
    for (OutboxEntity row : rows) {
      maxSequence = Math.max(maxSequence, row.getSequenceNo());
      revision = Math.max(revision, row.getAggregateRevision());
      if (row.getPrimitiveChanges() != null) {
        primitiveChanges.addAll(PrimitiveBucket.fromMaps(parseList(row.getPrimitiveChanges())));
      }
      if (row.getPaFiscalCode() != null && row.getPaymentChanges() != null) {
        paymentChanges.add(new HistoryEntry.PaymentChange(
            row.getPaFiscalCode(), row.getNoticeNumber(),
            row.getPaymentRevision() == null ? revision : row.getPaymentRevision(),
            parse(row.getPaymentChanges()), row.isSuppressPayment()));
      }
    }

    HistoryEntry entry = new HistoryEntry(maxSequence, revision, Instant.now(), before, after,
        paymentChanges, freshnessService.current(), primitiveChanges);
    synchronized (hub) {
      hub.publish(entry);
      if (paymentChanges.stream().anyMatch(change ->
          Boolean.TRUE.equals(change.changes().get("deleted"))
              || hasRemovals(change.changes(), "removedAttemptIds")
              || hasRemovals(change.changes(), "removedRecipientIds"))) {
        hub.expireReplay();
      }
      hub.setLastSnapshot(after);
    }

    Instant now = Instant.now();
    for (OutboxEntity row : rows) {
      repository.markPublished(row.getId(), now);
    }
  }

  private static boolean hasRemovals(Map<String, Object> changes, String key) {
    return changes.get(key) instanceof List<?> ids && !ids.isEmpty();
  }

  private List<Map<String, Object>> parseList(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Invalid outbox primitive changes JSON", e);
    }
  }

  private Map<String, Object> parse(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Invalid outbox payment changes JSON", e);
    }
  }

  /** Replay snapshots never retain raw tokens or retention bookkeeping. */
  private static List<DomainPayment> safeSnapshot(List<DomainPayment> payments) {
    for (DomainPayment p : payments) {
      for (DomainAttempt a : p.getAttempts()) {
        a.setPaymentToken(null);
      }
    }
    return payments;
  }
}
