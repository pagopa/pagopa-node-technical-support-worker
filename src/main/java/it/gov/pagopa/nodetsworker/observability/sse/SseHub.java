package it.gov.pagopa.nodetsworker.observability.sse;

import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveLogic;
import it.gov.pagopa.nodetsworker.observability.domain.ProjectionLogic;
import it.gov.pagopa.nodetsworker.observability.dto.AggregateDto;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.service.AggregationService;
import it.gov.pagopa.nodetsworker.observability.service.Caller;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory SSE fan-out and bounded replay store (100 events / 5 minutes), mirroring the publisher
 * semantics of {@code mocks/server.mjs}: aggregate deltas are recomputed per subscriber from the
 * stored before/after snapshots; payment deltas are forwarded; cursors are opaque and stable across
 * replays. The shared cross-instance channel of production is not simulated.
 */
@ApplicationScoped
public class SseHub {

  private final SecureRandom random = new SecureRandom();
  private final Set<SseClient> clients = ConcurrentHashMap.newKeySet();
  private final Deque<HistoryEntry> history = new ArrayDeque<>();
  private final Map<String, ReplayCursor> replayCursors = new ConcurrentHashMap<>();

  private volatile Sse sse;
  private volatile List<DomainPayment> lastSnapshot;

  @Inject
  ObservabilityConfig config;

  public void bindSse(Sse sse) {
    if (this.sse == null) {
      this.sse = sse;
    }
  }

  public List<DomainPayment> lastSnapshot() {
    return lastSnapshot;
  }

  public void setLastSnapshot(List<DomainPayment> snapshot) {
    this.lastSnapshot = snapshot;
  }

  public void register(SseClient client) {
    clients.add(client);
  }

  public void unregister(SseClient client) {
    clients.remove(client);
  }

  public OutboundSseEvent heartbeat() {
    return sse.newEventBuilder().comment("heartbeat").build();
  }

  // --- connection bootstrap -------------------------------------------------

  /**
   * Resolve a {@code Last-Event-ID} cursor, or {@code null} when none/invalid rules apply. Throws the
   * contract errors on a malformed id, an expired/unknown id or a scope mismatch.
   */
  public synchronized ReplayCursor resolveCursor(String lastEventId, Caller caller, Map<String, String> scope) {
    return resolveCursorLocked(lastEventId, caller, scope);
  }

  private ReplayCursor resolveCursorLocked(String lastEventId, Caller caller, Map<String, String> scope) {
    if (lastEventId == null) {
      return null;
    }
    if (!lastEventId.matches("[\\w-]{32}")) {
      throw Errors.invalidRequest("Cursore SSE non valido.");
    }
    pruneReplay();
    ReplayCursor cursor = replayCursors.get(lastEventId);
    if (cursor == null || cursor.expires <= System.currentTimeMillis()) {
      throw Errors.sseCursorExpired();
    }
    if (!cursor.scopeKey.equals(scopeKey(caller, scope))) {
      throw Errors.invalidRequest("Cursore SSE non appartenente allo scope.");
    }
    return cursor;
  }

  /** Build the initial frames for a new stream: replayed events (if reconnecting), then {@code ready}. */
  public synchronized List<OutboundSseEvent> initialEvents(
      SseClient client, ReplayCursor cursor, long readyRevision) {
    return initialEventsLocked(client, cursor, readyRevision);
  }

  /**
   * Revalidate the client's cursor, capture replay and ready frames, and register the client while
   * holding the publication monitor.
   */
  public synchronized List<OutboundSseEvent> registerAndInitialEvents(
      SseClient client, String lastEventId, long readyRevision) {
    ReplayCursor cursor = resolveCursorLocked(lastEventId, client.caller(), client.scope());
    List<OutboundSseEvent> events = initialEventsLocked(client, cursor, readyRevision);
    clients.add(client);
    return events;
  }

  private List<OutboundSseEvent> initialEventsLocked(
      SseClient client, ReplayCursor cursor, long suppliedRevision) {
    pruneReplay();
    List<OutboundSseEvent> events = new ArrayList<>();
    if (cursor != null) {
      for (HistoryEntry entry : List.copyOf(history)) {
        List<Map<String, Object>> dataItems = eventData(client, entry);
        for (int ordinal = 0; ordinal < dataItems.size(); ordinal++) {
          if (entry.sequence() > cursor.sequence
              || (entry.sequence() == cursor.sequence && ordinal > cursor.ordinal)) {
            Map<String, Object> data = dataItems.get(ordinal);
            events.add(buildAndTrack(client, entry.sequence(), ordinal, (String) data.get("type"), data));
          }
        }
      }
    }
    Map<String, Object> ready = new LinkedHashMap<>();
    ready.put("type", "ready");
    ready.put("scope", client.scope());
    long latestHistoryRevision = history.isEmpty() ? 0L : history.peekLast().revision();
    ready.put("projectionRevision", Math.max(suppliedRevision, latestHistoryRevision));
    if (!client.isPayment() && !isPaymentList(client)) {
      ready.put("window", window(client));
    }
    events.add(buildAndTrack(client, currentSequence(), Integer.MAX_VALUE, "ready", ready));
    return events;
  }

  // --- publication ----------------------------------------------------------

  public synchronized void publish(HistoryEntry entry) {
    history.addLast(entry);
    pruneReplay();
    for (SseClient client : clients) {
      List<Map<String, Object>> dataItems = eventData(client, entry);
      for (int ordinal = 0; ordinal < dataItems.size(); ordinal++) {
        Map<String, Object> data = dataItems.get(ordinal);
        client.emit(buildAndTrack(client, entry.sequence(), ordinal, (String) data.get("type"), data));
      }
    }
  }

  // --- per-subscriber event data --------------------------------------------

  private List<Map<String, Object>> eventData(SseClient client, HistoryEntry entry) {
    if (isPaymentList(client)) {
      List<Map<String, Object>> events = new ArrayList<>();
      for (var key : client.paymentKeys()) {
        for (HistoryEntry.PaymentChange change : entry.paymentChanges()) {
          if (!change.suppressed()
              && change.paFiscalCode().equals(key.paFiscalCode())
              && change.noticeNumber().equals(key.noticeNumber())) {
            events.add(paymentData(client, change));
          }
        }
      }
      return events;
    }
    if (client.isPayment()) {
      List<Map<String, Object>> events = new ArrayList<>();
      for (HistoryEntry.PaymentChange change : entry.paymentChanges()) {
        if (change.suppressed()) {
          continue;
        }
        if (change.paFiscalCode().equals(client.scope().get("paFiscalCode"))
            && change.noticeNumber().equals(client.scope().get("noticeNumber"))) {
          events.add(paymentData(client, change));
        }
      }
      return events;
    }
    List<DomainPayment> before = ProjectionLogic.selected(entry.before(), client.filters(), client.caller().allowedPa());
    List<DomainPayment> after = ProjectionLogic.selected(entry.after(), client.filters(), client.caller().allowedPa());
    AggregateDto aggBefore = AggregationService.aggregate(before, client.from(), client.to(), entry.createdAt(), entry.revision(),
        client.live());
    AggregateDto aggAfter = AggregationService.aggregate(after, client.from(), client.to(), entry.createdAt(), entry.revision(),
        client.live());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("type", "aggregate.delta");
    data.put("scope", client.scope());
    data.put("projectionRevision", entry.revision());
    Map<String, Object> changes = AggregateDeltaBuilder.changes(aggBefore, aggAfter);
    // Primitive counters exist only on the overview; other aggregate scopes carry an empty array.
    changes.put("primitives", "overview".equals(client.scopeType())
        ? PrimitiveLogic.rows(entry.primitiveChanges(), client.filters(), client.from(), client.to(),
            client.caller().allowedPa(), client.live())
        : List.of());
    data.put("changes", changes);
    data.put("freshness", entry.freshness());
    return List.of(data);
  }

  private Map<String, Object> paymentData(SseClient client, HistoryEntry.PaymentChange change) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("type", "payment.delta");
    data.put("scope", client.scope());
    data.put("paFiscalCode", change.paFiscalCode());
    data.put("noticeNumber", change.noticeNumber());
    data.put("projectionRevision", change.projectionRevision());
    data.put("changes", change.changes());
    return data;
  }

  // --- replay bookkeeping ---------------------------------------------------

  private OutboundSseEvent buildAndTrack(
      SseClient client, long sequence, int ordinal, String type, Map<String, Object> data) {
    String scopeKey = scopeKey(client.caller(), client.scope());
    String paymentIdentity = "payment.delta".equals(type)
        ? "|" + data.get("paFiscalCode") + "|" + data.get("noticeNumber")
        : "";
    String stableKey = scopeKey + "|" + windowKey(client) + "|" + sequence + "|" + ordinal + "|"
        + type + paymentIdentity;
    String id = null;
    if (!"ready".equals(type)) {
      for (Map.Entry<String, ReplayCursor> e : replayCursors.entrySet()) {
        if (e.getValue().stableKey.equals(stableKey) && e.getValue().expires > System.currentTimeMillis()) {
          id = e.getKey();
          break;
        }
      }
    }
    if (id == null) {
      id = opaque();
    }
    replayCursors.put(id, new ReplayCursor(sequence, ordinal, scopeKey, client.from(), client.to(), stableKey,
        System.currentTimeMillis() + config.sseReplayTtlSeconds() * 1000L));
    return sse.newEventBuilder().id(id).name(type).mediaType(jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE)
        .data(data).build();
  }

  private synchronized void pruneReplay() {
    while (history.size() > config.sseReplayLimit()
        || (!history.isEmpty() && history.peekFirst().createdAt().toEpochMilli()
            + config.sseReplayTtlSeconds() * 1000L <= System.currentTimeMillis())) {
      history.pollFirst();
    }
    long oldest = history.isEmpty() ? currentSequence() + 1 : history.peekFirst().sequence();
    long now = System.currentTimeMillis();
    replayCursors.entrySet().removeIf(e -> e.getValue().expires <= now || e.getValue().sequence < oldest - 1);
  }

  /** Discard the replay history and cursors: a retention sweep or an explicit expiry is a resync boundary. */
  public synchronized void expireReplay() {
    history.clear();
    replayCursors.clear();
  }

  private long currentSequence() {
    return history.isEmpty() ? 0 : history.peekLast().sequence();
  }

  private Map<String, String> window(SseClient client) {
    Map<String, String> w = new LinkedHashMap<>();
    w.put("from", it.gov.pagopa.nodetsworker.observability.util.IsoTime.format(client.from()));
    w.put("to", it.gov.pagopa.nodetsworker.observability.util.IsoTime.format(client.to()));
    return w;
  }

  private String windowKey(SseClient client) {
    return client.isPayment() || isPaymentList(client) ? "-" : client.from() + "/" + client.to();
  }

  private boolean isPaymentList(SseClient client) {
    return "payment-list".equals(client.scope().get("scope"));
  }

  public String scopeKey(Caller caller, Map<String, String> scope) {
    return caller.id() + "|" + new java.util.TreeMap<>(scope);
  }

  private String opaque() {
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** A resumable SSE cursor bound to a scope, window and replay sequence. */
  public static final class ReplayCursor {
    final long sequence;
    final int ordinal;
    final String scopeKey;
    final Instant from;
    final Instant to;
    final String stableKey;
    final long expires;

    ReplayCursor(long sequence, int ordinal, String scopeKey, Instant from, Instant to,
        String stableKey, long expires) {
      this.sequence = sequence;
      this.ordinal = ordinal;
      this.scopeKey = scopeKey;
      this.from = from;
      this.to = to;
      this.stableKey = stableKey;
      this.expires = expires;
    }

    public long sequence() {
      return sequence;
    }

    public Instant from() {
      return from;
    }

    public Instant to() {
      return to;
    }
  }
}
