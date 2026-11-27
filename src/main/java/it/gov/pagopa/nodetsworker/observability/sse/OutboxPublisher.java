package it.gov.pagopa.nodetsworker.observability.sse;

import it.gov.pagopa.nodetsworker.datasource.ObservabilityDatasourceDisabled;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
  static final int DEFAULT_BATCH_SIZE = 200;

  /** Upper bound of outbox rows drained (and of payments reloaded) per tick. */
  @ConfigProperty(name = "observability.outbox.batch-size", defaultValue = "200")
  int batchSize = DEFAULT_BATCH_SIZE;

  @Inject
  ProjectionRepository repository;

  @Inject
  SseHub hub;

  @Inject
  FreshnessService freshnessService;

  @Inject
  ObjectMapper objectMapper;

  @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
      skipExecutionIf = ObservabilityDatasourceDisabled.class)
  void drain() {
    try {
      poll();
    } catch (RuntimeException e) {
      LOG.debug("Outbox drain skipped", e);
    }
  }

  void poll() {
    List<OutboxEntity> rows = repository.unpublishedOutbox(batchSize);
    List<DomainPayment> previous = hub.lastSnapshot();

    if (previous == null) {
      // Once per process: the baseline snapshot every following delta is merged into.
      previous = safeSnapshot(repository.loadVisible(null));
      if (rows.isEmpty()) {
        hub.setLastSnapshot(previous);
        return;
      }
      publish(rows, previous, previous);
      return;
    }
    if (rows.isEmpty()) {
      return;
    }
    // Every projection write stores its outbox row in the same transaction, so the payments named by the
    // batch are the only ones that may differ from the previous snapshot.
    publish(rows, previous, merge(previous, rows));
  }

  private List<DomainPayment> merge(List<DomainPayment> previous, List<OutboxEntity> rows) {
    Set<ProjectionRepository.PaymentKey> keys = new LinkedHashSet<>();
    for (OutboxEntity row : rows) {
      if (row.getPaFiscalCode() != null) {
        keys.add(new ProjectionRepository.PaymentKey(row.getPaFiscalCode(), row.getNoticeNumber()));
      }
    }
    if (keys.isEmpty()) {
      return previous;
    }
    Map<String, DomainPayment> reloaded = new LinkedHashMap<>();
    for (DomainPayment p : safeSnapshot(repository.loadByKeys(new ArrayList<>(keys)))) {
      reloaded.put(p.key(), p);
    }
    Set<String> touched = new HashSet<>();
    for (ProjectionRepository.PaymentKey key : keys) {
      touched.add(key.paFiscalCode() + "/" + key.noticeNumber());
    }
    List<DomainPayment> after = new ArrayList<>(previous.size() + reloaded.size());
    for (DomainPayment p : previous) {
      if (!touched.contains(p.key())) {
        after.add(p);
      } else if (reloaded.containsKey(p.key())) {
        after.add(reloaded.remove(p.key()));
      }
    }
    after.addAll(reloaded.values());
    return after;
  }

  private void publish(List<OutboxEntity> rows, List<DomainPayment> before, List<DomainPayment> after) {
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

    repository.markPublished(rows.stream().map(OutboxEntity::getId).toList(), Instant.now());
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
