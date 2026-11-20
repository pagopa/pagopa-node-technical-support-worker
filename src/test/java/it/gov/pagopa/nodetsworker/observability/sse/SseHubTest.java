package it.gov.pagopa.nodetsworker.observability.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveBucket;
import it.gov.pagopa.nodetsworker.observability.domain.DomainRecipient;
import it.gov.pagopa.nodetsworker.observability.domain.TimelineItem;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import it.gov.pagopa.nodetsworker.observability.service.Caller;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityConfig;
import it.gov.pagopa.nodetsworker.observability.service.QuerySupport;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SseHubTest {

  private static final String PA = "00000000001";
  private static final String FIRST_NOTICE = "300000000000000001";
  private static final String SECOND_NOTICE = "300000000000000002";
  private static final String UNSELECTED_NOTICE = "300000000000000003";

  private SseHub hub;
  private Caller caller;
  private Map<String, String> scope;
  private EventState eventState;

  @BeforeEach
  void setUp() {
    hub = new SseHub();
    hub.config = mock(ObservabilityConfig.class);
    when(hub.config.sseReplayLimit()).thenReturn(100);
    when(hub.config.sseReplayTtlSeconds()).thenReturn(300L);
    eventState = new EventState();
    hub.bindSse(mockSse(eventState));
    caller = Caller.implicit();
    scope = Map.of("scope", "payment-list", "keys",
        PA + ":" + FIRST_NOTICE + "," + PA + ":" + SECOND_NOTICE);
  }

  @Test
  void emitsSortedSelectedPaymentDeltasForLiveAndReplay() {
    SseClient client = new SseClient("client", caller, scope, null, null);
    assertEquals(List.of(
        new QuerySupport.PaymentKey(PA, FIRST_NOTICE),
        new QuerySupport.PaymentKey(PA, SECOND_NOTICE)), client.paymentKeys());
    List<OutboundSseEvent> liveEvents = new ArrayList<>();
    client.processor().subscribe().with(liveEvents::add);
    hub.register(client);

    List<OutboundSseEvent> beforePublication = hub.initialEvents(client, null, 7L);
    String readyId = beforePublication.get(0).getId();
    HistoryEntry entry = selectedChangesEntry(1L);
    hub.publish(entry);

    assertPaymentDeltas(liveEvents);

    SseHub.ReplayCursor cursor = hub.resolveCursor(readyId, caller, scope);
    SseClient replayClient = new SseClient("replay-client", caller, scope, null, null);
    List<OutboundSseEvent> replayEvents = hub.initialEvents(replayClient, cursor, 7L);

    assertEquals(3, replayEvents.size());
    assertPaymentDeltas(replayEvents.subList(0, 2));
    assertEquals("ready", replayEvents.get(2).getName());
  }

  @Test
  void emitsEveryUnsuppressedChangeForSelectedSinglePayment() {
    Map<String, String> paymentScope = Map.of(
        "scope", "payment", "paFiscalCode", PA, "noticeNumber", FIRST_NOTICE);
    SseClient client = new SseClient("client", caller, paymentScope, null, null);
    List<OutboundSseEvent> events = new ArrayList<>();
    client.processor().subscribe().with(events::add);
    hub.register(client);

    hub.publish(new HistoryEntry(1L, 1L, Instant.now(), List.of(), List.of(), List.of(
        paymentChange(FIRST_NOTICE, "first", false),
        paymentChange(UNSELECTED_NOTICE, "unselected", false),
        paymentChange(FIRST_NOTICE, "second", false),
        paymentChange(FIRST_NOTICE, "suppressed", true)), null));

    assertEquals(2, events.size());
    assertPaymentChange(events.get(0), paymentScope, FIRST_NOTICE, "first");
    assertPaymentChange(events.get(1), paymentScope, FIRST_NOTICE, "second");
  }

  @Test
  @SuppressWarnings("unchecked")
  void readyRevisionIsTheMaximumOfSuppliedAndLatestHistoryRevision() {
    hub.publish(new HistoryEntry(1L, 12L, Instant.now(), List.of(), List.of(), List.of(), null));
    SseClient client = new SseClient("client", caller, scope, null, null);

    List<OutboundSseEvent> events = hub.registerAndInitialEvents(client, null, 5L);

    assertEquals(12L, ((Map<String, Object>) events.get(0).getData()).get("projectionRevision"));

    SseClient newerClient = new SseClient("newer-client", caller, scope, null, null);
    List<OutboundSseEvent> newerEvents = hub.registerAndInitialEvents(newerClient, null, 20L);
    assertEquals(20L, ((Map<String, Object>) newerEvents.get(0).getData()).get("projectionRevision"));
  }

  @Test
  void recipientOnlyAggregateUpdatesAndRemovalsCarryStatesMatricesTrendsAndDurations() {
    Instant start = Instant.parse("2026-10-20T10:00:00Z");
    DomainRecipient recipient = DomainRecipient.builder().recipientId("rcp-test")
        .stazioneRecipient("station")
        .timeline(List.of(
            new TimelineItem("NOTICE_GENERATED", start, TimestampBasis.SOURCE_EVENT, "generate"),
            new TimelineItem("NOTICE_SENT", start.plusSeconds(1), TimestampBasis.SOURCE_EVENT, "send")))
        .build();
    DomainPayment payment = DomainPayment.builder().paFiscalCode(PA).noticeNumber(FIRST_NOTICE)
        .recipients(List.of(recipient)).build();
    SseClient client = new SseClient("recipient-aggregate", caller, Map.of("scope", "overview"),
        start.minusSeconds(1), start.plusSeconds(2));
    hub.register(client);
    List<OutboundSseEvent> events = new ArrayList<>();
    client.processor().subscribe().with(events::add);
    hub.publish(new HistoryEntry(1, 2, start, List.of(), List.of(payment), List.of(), null));
    hub.publish(new HistoryEntry(2, 3, start, List.of(payment), List.of(), List.of(), null));
    ObjectMapper mapper = new ObjectMapper();
    var added = mapper.valueToTree(events.get(0).getData()).path("changes");
    assertEquals("aggregate.delta", events.get(0).getName());
    assertEquals("NOTICE_SENT", added.at("/recipientStates/0/state").asText());
    assertEquals(1, added.at("/recipientStates/0/count").asInt());
    assertEquals("station", added.at("/matrices/recipient/0/value").asText());
    assertEquals("recipient", added.at("/transitions/0/entity").asText());
    assertEquals("recipient", added.at("/durationSnapshot/0/entity").asText());
    assertEquals(1000, added.at("/durationSnapshot/0/averageMs").asInt());
    var removed = mapper.valueToTree(events.get(1).getData()).path("changes");
    assertEquals(-1, removed.at("/recipientStates/0/count").asInt());
    assertEquals(-1, removed.at("/matrices/recipient/0/count").asInt());
    assertTrue(removed.get("durationSnapshot").isEmpty());
  }

  @Test
  void aggregateDeltasCarryFilteredPrimitiveChangesOnlyOnTheOverview() {
    Instant minute = Instant.parse("2026-10-20T10:00:00Z");
    List<PrimitiveBucket> changes = List.of(
        new PrimitiveBucket(minute, "SVC_A", "activatePaymentNotice", "PSP1", null, null, 2, 1, 1),
        new PrimitiveBucket(minute, "SVC_B", "verifyPaymentNotice", "PSP2", null, null, 3, 3, 0),
        new PrimitiveBucket(minute.plusSeconds(3600), "SVC_A", "activatePaymentNotice", "PSP1", null, null, 9, 9, 0));
    SseClient overview = new SseClient("overview", caller, Map.of("scope", "overview", "psp", "PSP1"),
        minute, minute.plusSeconds(600));
    SseClient creditor = new SseClient("creditor", caller,
        Map.of("scope", "creditor", "paFiscalCode", PA), minute, minute.plusSeconds(600));
    hub.register(overview);
    hub.register(creditor);
    List<OutboundSseEvent> overviewEvents = new ArrayList<>();
    List<OutboundSseEvent> creditorEvents = new ArrayList<>();
    overview.processor().subscribe().with(overviewEvents::add);
    creditor.processor().subscribe().with(creditorEvents::add);

    hub.publish(new HistoryEntry(1, 2, minute, List.of(), List.of(), List.of(), null, changes));

    ObjectMapper mapper = new ObjectMapper();
    var primitives = mapper.valueToTree(overviewEvents.get(0).getData()).at("/changes/primitives");
    assertEquals(1, primitives.size());
    assertEquals("SVC_A", primitives.get(0).get("serviceIdentifier").asText());
    assertEquals("activatePaymentNotice", primitives.get(0).get("primitive").asText());
    assertEquals(2, primitives.get(0).get("requests").asInt());
    assertEquals(1, primitives.get(0).get("warnings").asInt());
    assertTrue(mapper.valueToTree(creditorEvents.get(0).getData()).at("/changes/primitives").isEmpty());
  }

  @Test
  void liveOverviewStaysOpenOnTheRightForNewPrimitiveRequests() {
    Instant minute = Instant.parse("2026-10-20T10:00:00Z");
    List<PrimitiveBucket> later = List.of(
        new PrimitiveBucket(minute.plusSeconds(1800), "SVC_A", "activatePaymentNotice", null, null, null, 1, 0, 0));
    SseClient closed = new SseClient("closed", caller,
        Map.of("scope", "overview"), minute, minute.plusSeconds(600));
    SseClient live = new SseClient("live", caller,
        Map.of("scope", "overview", "live", "true"), minute, minute.plusSeconds(600));
    hub.register(closed);
    hub.register(live);
    List<OutboundSseEvent> closedEvents = new ArrayList<>();
    List<OutboundSseEvent> liveEvents = new ArrayList<>();
    closed.processor().subscribe().with(closedEvents::add);
    live.processor().subscribe().with(liveEvents::add);

    hub.publish(new HistoryEntry(1, 2, minute, List.of(), List.of(), List.of(), null, later));

    ObjectMapper mapper = new ObjectMapper();
    assertTrue(mapper.valueToTree(closedEvents.get(0).getData()).at("/changes/primitives").isEmpty());
    assertEquals(1, mapper.valueToTree(liveEvents.get(0).getData()).at("/changes/primitives/0/requests").asInt());
  }

  @Test
  void recipientDeltaAndIndependentRemovalAreForwardedWithoutAttemptChanges() {
    Map<String, String> paymentScope = Map.of("scope", "payment",
        "paFiscalCode", PA, "noticeNumber", FIRST_NOTICE);
    SseClient client = new SseClient("recipient-payment", caller, paymentScope, null, null);
    hub.register(client);
    List<OutboundSseEvent> events = new ArrayList<>();
    client.processor().subscribe().with(events::add);
    Map<String, Object> changes = Map.of("logicalTransitions", List.of(), "attemptChanges", List.of(),
        "removedAttemptIds", List.of(), "recipientChanges", List.of(),
        "removedRecipientIds", List.of("rcp-test"), "deleted", false);
    hub.publish(new HistoryEntry(1, 8, Instant.now(), List.of(), List.of(),
        List.of(new HistoryEntry.PaymentChange(PA, FIRST_NOTICE, 4, changes, false)), null));
    var data = new ObjectMapper().valueToTree(events.get(0).getData());
    assertEquals("payment.delta", data.path("type").asText());
    assertEquals(4, data.path("projectionRevision").asInt());
    assertEquals("rcp-test", data.at("/changes/removedRecipientIds/0").asText());
    assertTrue(data.at("/changes/attemptChanges").isEmpty());
  }

  @Test
  void publicationAfterBootstrapIsBufferedAndReplayableFromReadyCursor() {
    SseClient client = new SseClient("client", caller, scope, null, null);
    List<OutboundSseEvent> initial = hub.registerAndInitialEvents(client, null, 7L);
    String readyId = initial.get(initial.size() - 1).getId();
    hub.publish(selectedChangesEntry(1L));

    List<OutboundSseEvent> liveEvents = new ArrayList<>();
    client.processor().subscribe().with(liveEvents::add);
    assertPaymentDeltas(liveEvents);

    SseClient reconnect = new SseClient("reconnect", caller, scope, null, null);
    List<OutboundSseEvent> replay = hub.registerAndInitialEvents(reconnect, readyId, 7L);
    assertEquals(3, replay.size());
    assertPaymentDeltas(replay.subList(0, 2));
    assertEquals("ready", replay.get(2).getName());
  }

  @Test
  void reconnectingFromFirstPaymentListDeltaReplaysOnlyTheNextDeltaWithDistinctIds() {
    SseClient client = new SseClient("client", caller, scope, null, null);
    hub.registerAndInitialEvents(client, null, 7L);
    hub.publish(selectedChangesEntry(1L));

    List<OutboundSseEvent> events = new ArrayList<>();
    client.processor().subscribe().with(events::add);
    assertPaymentDeltas(events);
    assertTrue(!events.get(0).getId().equals(events.get(1).getId()));

    SseClient reconnect = new SseClient("reconnect", caller, scope, null, null);
    List<OutboundSseEvent> replay = hub.registerAndInitialEvents(reconnect, events.get(0).getId(), 7L);

    assertEquals(2, replay.size());
    assertPaymentChange(replay.get(0), scope, SECOND_NOTICE, "second");
    assertEquals("ready", replay.get(1).getName());
    assertEquals(events.get(1).getId(), replay.get(0).getId());
  }

  @Test
  void paymentListCursorIdentityUsesCanonicalKeysAndRejectsChangedSelection() {
    String reversedKeys = PA + ":" + SECOND_NOTICE + "," + PA + ":" + FIRST_NOTICE;
    String canonicalKeys = QuerySupport.formatPaymentKeys(QuerySupport.parsePaymentKeys(reversedKeys));
    Map<String, String> canonicalScope = Map.of("scope", "payment-list", "keys", canonicalKeys);
    SseClient client = new SseClient("client", caller, canonicalScope, null, null);
    String readyId = hub.registerAndInitialEvents(client, null, 7L).get(0).getId();

    String sameKeysInOriginalOrder = QuerySupport.formatPaymentKeys(QuerySupport.parsePaymentKeys(
        PA + ":" + FIRST_NOTICE + "," + PA + ":" + SECOND_NOTICE));
    Map<String, String> reorderedScope = Map.of("keys", sameKeysInOriginalOrder, "scope", "payment-list");

    assertEquals(scope.get("keys"), canonicalKeys);
    assertEquals(hub.scopeKey(caller, canonicalScope), hub.scopeKey(caller, reorderedScope));
    assertEquals(0L, hub.resolveCursor(readyId, caller, reorderedScope).sequence());

    Map<String, String> changedScope = Map.of(
        "scope", "payment-list", "keys", PA + ":" + FIRST_NOTICE + "," + PA + ":" + UNSELECTED_NOTICE);
    ObservabilityException exception = org.junit.jupiter.api.Assertions.assertThrows(
        ObservabilityException.class, () -> hub.resolveCursor(readyId, caller, changedScope));
    assertEquals(400, exception.getStatus());
  }

  @Test
  void concurrentBootstrapPublicationAndCursorResolutionPreserveEveryPostReadySequence() throws Exception {
    when(hub.config.sseReplayLimit()).thenReturn(1000);
    SseClient seed = new SseClient("seed", caller, scope, null, null);
    String readyId = hub.registerAndInitialEvents(seed, null, 0L).get(0).getId();
    hub.unregister(seed);
    ExecutorService executor = Executors.newFixedThreadPool(3);
    CountDownLatch start = new CountDownLatch(1);
    CyclicBarrier simultaneousStart = new CyclicBarrier(3);
    CyclicBarrier publicationAndResolution = new CyclicBarrier(2);
    SseClient client = new SseClient("concurrent", caller, scope, null, null);
    List<OutboundSseEvent> initial = new ArrayList<>();
    try {
      Future<?> bootstrap = executor.submit(() -> {
        await(start);
        await(simultaneousStart);
        initial.addAll(hub.registerAndInitialEvents(client, readyId, 0L));
      });
      Future<?> publisher = executor.submit(() -> {
        await(start);
        await(simultaneousStart);
        for (long sequence = 1; sequence <= 80; sequence++) {
          hub.publish(singlePaymentEntry(sequence));
          await(publicationAndResolution);
        }
      });
      Future<?> resolver = executor.submit(() -> {
        await(start);
        await(simultaneousStart);
        for (long sequence = 1; sequence <= 80; sequence++) {
          hub.resolveCursor(readyId, caller, scope);
          await(publicationAndResolution);
        }
      });
      start.countDown();
      bootstrap.get(10, TimeUnit.SECONDS);
      publisher.get(10, TimeUnit.SECONDS);
      resolver.get(10, TimeUnit.SECONDS);

      String readyCursorId = initial.get(initial.size() - 1).getId();
      long readySequence = hub.resolveCursor(readyCursorId, caller, scope).sequence();
      List<OutboundSseEvent> buffered = new ArrayList<>();
      client.processor().subscribe().with(buffered::add);
      assertSequencesExactlyOnce(buffered, readySequence + 1, 80);

      SseClient reconnect = new SseClient("reconnect", caller, scope, null, null);
      List<OutboundSseEvent> replay = hub.registerAndInitialEvents(reconnect, readyCursorId, 0L);
      assertSequencesExactlyOnce(replay.subList(0, replay.size() - 1), readySequence + 1, 80);
      assertEquals("ready", replay.get(replay.size() - 1).getName());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void publicationAndCursorResolutionWaitForBootstrapMonitorAndPreservePostReadyDelta() throws Exception {
    SseClient seed = new SseClient("seed", caller, scope, null, null);
    String priorReadyId = hub.registerAndInitialEvents(seed, null, 0L).get(0).getId();
    hub.unregister(seed);

    CountDownLatch readyBuilding = new CountDownLatch(1);
    CountDownLatch releaseReady = new CountDownLatch(1);
    eventState.onReadyBuild = () -> {
      eventState.onReadyBuild = null;
      readyBuilding.countDown();
      await(releaseReady);
    };

    ExecutorService executor = Executors.newFixedThreadPool(3);
    AtomicReference<Thread> bootstrapThread = new AtomicReference<>();
    AtomicReference<Thread> publisherThread = new AtomicReference<>();
    AtomicReference<Thread> resolverThread = new AtomicReference<>();
    AtomicReference<SseHub.ReplayCursor> resolvedCursor = new AtomicReference<>();
    CountDownLatch operationsAttempting = new CountDownLatch(2);
    SseClient client = new SseClient("blocked-bootstrap", caller, scope, null, null);
    List<OutboundSseEvent> initial = new ArrayList<>();
    try {
      Future<?> bootstrap = executor.submit(() -> {
        bootstrapThread.set(Thread.currentThread());
        initial.addAll(hub.registerAndInitialEvents(client, priorReadyId, 7L));
      });
      assertTrue(readyBuilding.await(5, TimeUnit.SECONDS), "Bootstrap did not reach ready event construction");

      Future<?> publisher = executor.submit(() -> {
        publisherThread.set(Thread.currentThread());
        operationsAttempting.countDown();
        hub.publish(singlePaymentEntry(1L));
      });
      Future<?> resolver = executor.submit(() -> {
        resolverThread.set(Thread.currentThread());
        operationsAttempting.countDown();
        resolvedCursor.set(hub.resolveCursor(priorReadyId, caller, scope));
      });

      assertTrue(operationsAttempting.await(5, TimeUnit.SECONDS), "Operations did not start");
      awaitBlockedOnHubMonitor(bootstrapThread.get(), publisherThread.get(), resolverThread.get());

      releaseReady.countDown();
      bootstrap.get(5, TimeUnit.SECONDS);
      publisher.get(5, TimeUnit.SECONDS);
      resolver.get(5, TimeUnit.SECONDS);

      assertEquals(0L, resolvedCursor.get().sequence());
      assertEquals(1, initial.size());
      String readyId = initial.get(initial.size() - 1).getId();
      assertEquals("ready", initial.get(initial.size() - 1).getName());

      List<OutboundSseEvent> buffered = new ArrayList<>();
      client.processor().subscribe().with(buffered::add);
      assertEquals(1, buffered.size());
      assertPaymentChange(buffered.get(0), scope, FIRST_NOTICE, "sequence-1");

      SseClient reconnect = new SseClient("reconnect", caller, scope, null, null);
      List<OutboundSseEvent> replay = hub.registerAndInitialEvents(reconnect, readyId, 7L);
      assertEquals(2, replay.size());
      assertPaymentChange(replay.get(0), scope, FIRST_NOTICE, "sequence-1");
      assertEquals(buffered.get(0).getId(), replay.get(0).getId());
      assertEquals("ready", replay.get(1).getName());
    } finally {
      releaseReady.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void concurrentReplayExpiryPublicationAndCursorResolutionKeepHubUsable() throws Exception {
    SseClient seed = new SseClient("seed", caller, scope, null, null);
    String oldReadyId = hub.registerAndInitialEvents(seed, null, 0L).get(0).getId();
    hub.unregister(seed);
    ExecutorService executor = Executors.newFixedThreadPool(3);
    CountDownLatch start = new CountDownLatch(1);
    CyclicBarrier simultaneousStart = new CyclicBarrier(3);
    try {
      Future<?> expiry = executor.submit(() -> {
        await(start);
        await(simultaneousStart);
        for (int i = 0; i < 80; i++) {
          hub.expireReplay();
        }
      });
      Future<?> publisher = executor.submit(() -> {
        await(start);
        await(simultaneousStart);
        for (long sequence = 1; sequence <= 80; sequence++) {
          hub.publish(singlePaymentEntry(sequence));
        }
      });
      Future<?> resolver = executor.submit(() -> {
        await(start);
        await(simultaneousStart);
        for (int i = 0; i < 80; i++) {
          resolveUnlessExpired(oldReadyId);
        }
      });
      start.countDown();
      expiry.get(10, TimeUnit.SECONDS);
      publisher.get(10, TimeUnit.SECONDS);
      resolver.get(10, TimeUnit.SECONDS);

      hub.publish(singlePaymentEntry(81L));
      SseClient fresh = new SseClient("fresh", caller, scope, null, null);
      List<OutboundSseEvent> freshInitial = hub.registerAndInitialEvents(fresh, null, 81L);
      String freshReadyId = freshInitial.get(freshInitial.size() - 1).getId();
      assertEquals(81L, hub.resolveCursor(freshReadyId, caller, scope).sequence());
      assertEquals("ready", freshInitial.get(freshInitial.size() - 1).getName());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void replaySizePruningExpiresCursorsOlderThanRetainedHistory() {
    when(hub.config.sseReplayLimit()).thenReturn(2);
    SseClient seed = new SseClient("seed", caller, scope, null, null);
    String oldReadyId = hub.registerAndInitialEvents(seed, null, 0L).get(0).getId();

    hub.publish(singlePaymentEntry(1L));
    hub.publish(singlePaymentEntry(2L));
    hub.publish(singlePaymentEntry(3L));

    assertCursorExpired(oldReadyId);
  }

  private void assertSequencesExactlyOnce(List<OutboundSseEvent> events, long first, long last) {
    List<Long> actual = events.stream()
        .map(event -> ((Number) ((Map<?, ?>) event.getData()).get("projectionRevision")).longValue())
        .toList();
    List<Long> expected = new ArrayList<>();
    for (long sequence = first; sequence <= last; sequence++) {
      expected.add(sequence);
    }
    assertEquals(expected, actual);
  }

  private static void await(CountDownLatch start) {
    try {
      if (!start.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for latch");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void await(CyclicBarrier barrier) {
    try {
      barrier.await(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void awaitBlockedOnHubMonitor(Thread bootstrap, Thread publisher, Thread resolver) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    var threadBean = ManagementFactory.getThreadMXBean();
    while (System.nanoTime() < deadline) {
      ThreadInfo publisherInfo = threadBean.getThreadInfo(publisher.getId());
      ThreadInfo resolverInfo = threadBean.getThreadInfo(resolver.getId());
      if (isBlockedOnHubMonitor(publisherInfo, bootstrap)
          && isBlockedOnHubMonitor(resolverInfo, bootstrap)) {
        return;
      }
      Thread.yield();
    }
    assertTrue(false, "Publisher and resolver did not both block on the bootstrap hub monitor: publisher="
        + threadBean.getThreadInfo(publisher.getId()) + ", resolver=" + threadBean.getThreadInfo(resolver.getId()));
  }

  private boolean isBlockedOnHubMonitor(ThreadInfo contender, Thread owner) {
    return contender != null
        && contender.getThreadState() == Thread.State.BLOCKED
        && contender.getLockOwnerId() == owner.getId()
        && contender.getLockName() != null
        && contender.getLockName().startsWith(SseHub.class.getName() + "@");
  }

  private void resolveUnlessExpired(String cursorId) {
    try {
      hub.resolveCursor(cursorId, caller, scope);
    } catch (ObservabilityException exception) {
      assertEquals(410, exception.getStatus());
      assertEquals("SSE_CURSOR_EXPIRED", exception.toBody().getCode());
    }
  }

  private void assertCursorExpired(String cursorId) {
    ObservabilityException exception = org.junit.jupiter.api.Assertions.assertThrows(
        ObservabilityException.class, () -> hub.resolveCursor(cursorId, caller, scope));
    assertEquals(410, exception.getStatus());
    assertEquals("SSE_CURSOR_EXPIRED", exception.toBody().getCode());
  }

  private void assertPaymentDeltas(List<OutboundSseEvent> events) {
    assertEquals(2, events.size());
    assertTrue(events.stream().allMatch(event -> "payment.delta".equals(event.getName())));
    assertPaymentChange(events.get(0), scope, FIRST_NOTICE, "first");
    assertPaymentChange(events.get(1), scope, SECOND_NOTICE, "second");
  }

  @SuppressWarnings("unchecked")
  private void assertPaymentChange(
      OutboundSseEvent event, Map<String, String> expectedScope, String noticeNumber, String value) {
    Map<String, Object> data = (Map<String, Object>) event.getData();
    assertEquals("payment.delta", data.get("type"));
    assertEquals(expectedScope, data.get("scope"));
    assertEquals(PA, data.get("paFiscalCode"));
    assertEquals(noticeNumber, data.get("noticeNumber"));
    assertEquals(Map.of("value", value), data.get("changes"));
  }

  private HistoryEntry selectedChangesEntry(long sequence) {
    return new HistoryEntry(sequence, sequence, Instant.now(), List.of(), List.of(), List.of(
        paymentChange(SECOND_NOTICE, "second", false),
        paymentChange(UNSELECTED_NOTICE, "unselected", false),
        paymentChange(FIRST_NOTICE, "first", false),
        paymentChange(SECOND_NOTICE, "suppressed", true)), null);
  }

  private HistoryEntry singlePaymentEntry(long sequence) {
    return new HistoryEntry(sequence, sequence, Instant.now(), List.of(), List.of(), List.of(
        new HistoryEntry.PaymentChange(PA, FIRST_NOTICE, sequence,
            Map.of("value", "sequence-" + sequence), false)), null);
  }

  private HistoryEntry.PaymentChange paymentChange(String noticeNumber, String value, boolean suppressed) {
    return new HistoryEntry.PaymentChange(PA, noticeNumber, 40L, Map.of("value", value), suppressed);
  }

  private Sse mockSse(EventState state) {
    Sse sse = mock(Sse.class);
    OutboundSseEvent.Builder builder = mock(OutboundSseEvent.Builder.class);
    when(sse.newEventBuilder()).thenReturn(builder);
    when(builder.id(anyString())).thenAnswer(invocation -> {
      state.id = invocation.getArgument(0);
      return builder;
    });
    when(builder.name(anyString())).thenAnswer(invocation -> {
      state.name = invocation.getArgument(0);
      return builder;
    });
    when(builder.mediaType(any(MediaType.class))).thenReturn(builder);
    when(builder.data(any())).thenAnswer(invocation -> {
      state.data = invocation.getArgument(0);
      return builder;
    });
    when(builder.comment(anyString())).thenReturn(builder);
    when(builder.build()).thenAnswer(invocation -> {
      OutboundSseEvent event = mock(OutboundSseEvent.class);
      when(event.getId()).thenReturn(state.id);
      when(event.getName()).thenReturn(state.name);
      when(event.getData()).thenReturn(state.data);
      if ("ready".equals(state.name) && state.onReadyBuild != null) {
        state.onReadyBuild.run();
      }
      return event;
    });
    return sse;
  }

  private static final class EventState {
    private String id;
    private String name;
    private Object data;
    private Runnable onReadyBuild;
  }
}
