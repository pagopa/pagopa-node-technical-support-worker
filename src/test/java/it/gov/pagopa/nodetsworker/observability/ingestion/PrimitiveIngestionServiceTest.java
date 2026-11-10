package it.gov.pagopa.nodetsworker.observability.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.observability.domain.Filters;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveBucket;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveDirection;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveLogic;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveWarningRow;
import it.gov.pagopa.nodetsworker.observability.dto.PrimitiveCountDto;
import it.gov.pagopa.nodetsworker.observability.dto.PrimitiveWarningDto;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.repository.models.OutboxEntity;
import it.gov.pagopa.nodetsworker.observability.service.Caller;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pairing, counting and timeout of primitive REQ/RESP events on H2 (spec §3.3.2). */
@QuarkusTest
class PrimitiveIngestionServiceTest {

  private static final String DAY = "2024-06-15T";
  private static final Instant FROM = Instant.parse("2024-06-15T00:00:00Z");
  private static final Instant TO = Instant.parse("2024-06-16T00:00:00Z");

  @Inject
  ReIngestionService service;

  @Inject
  PrimitiveIngestionService primitives;

  @Inject
  ProjectionRepository repository;

  @Inject
  ObservabilityService observability;

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  private static ReMessage leg(String subType, String session, String tipo, String uniqueId, String time,
      String serviceIdentifier) {
    ReMessage m = new ReMessage();
    m.setCategoriaEvento("INTERFACCIA");
    m.setSottoTipoEvento(subType);
    m.setEsito("RICEVUTA");
    m.setSessionId(session);
    m.setTipoEvento(tipo);
    m.setUniqueId(uniqueId);
    m.setDataOraEvento(DAY + time);
    m.setServiceIdentifier(serviceIdentifier);
    m.setPsp("PSP1");
    return m;
  }

  private static ReMessage req(String session, String uniqueId, String time, String service) {
    return leg("REQ", session, "activatePaymentNotice", uniqueId, time, service);
  }

  private static ReMessage resp(String session, String uniqueId, String time, String service) {
    return leg("RESP", session, "activatePaymentNotice", uniqueId, time, service);
  }

  private List<PrimitiveCountDto> rows(Filters filters) {
    em.flush();
    return PrimitiveLogic.rows(repository.primitiveBuckets(FROM, TO), filters, FROM, TO, null);
  }

  private List<PrimitiveCountDto> rows() {
    return rows(Filters.builder().build());
  }

  private static long sum(List<PrimitiveCountDto> rows, java.util.function.ToLongFunction<PrimitiveCountDto> field) {
    return rows.stream().mapToLong(field).sum();
  }

  private long calls() {
    return em.createQuery("SELECT COUNT(c) FROM PrimitiveCallEntity c", Long.class).getSingleResult();
  }

  @Test
  @TestTransaction
  void answeredRequestCountsOnceWithoutWarning() {
    assertEquals(ReIngestionService.Outcome.APPLIED,
        service.ingest(req("S1", "P-U1", "10:00:00.000", "SVC_A")));
    assertEquals(ReIngestionService.Outcome.APPLIED,
        service.ingest(resp("S1", "P-U2", "10:00:02.500", "SVC_A")));

    List<PrimitiveCountDto> rows = rows();
    assertEquals(1, rows.size());
    assertEquals("SVC_A", rows.get(0).getServiceIdentifier());
    assertEquals(1, rows.get(0).getRequests());
    assertEquals(1, rows.get(0).getResponses());
    assertEquals(0, rows.get(0).getWarnings());
    assertEquals(0, calls(), "the work row disappears once the call is settled");
  }

  @Test
  @TestTransaction
  void responseAfterMoreThanTenSecondsIsAWarning() {
    service.ingest(req("S2", "P-U3", "10:00:00.000", "SVC_A"));
    service.ingest(resp("S2", "P-U4", "10:00:10.001", "SVC_A"));

    PrimitiveCountDto row = rows().get(0);
    assertEquals(1, row.getResponses());
    assertEquals(1, row.getWarnings());

    service.ingest(req("S3", "P-U5", "10:00:00.000", "SVC_A"));
    service.ingest(resp("S3", "P-U6", "10:00:10.000", "SVC_A"));
    assertEquals(1, rows().get(0).getWarnings(), "exactly ten seconds is still within the budget");
  }

  @Test
  @TestTransaction
  void responseThatArrivesBeforeItsRequestIsPaired() {
    service.ingest(resp("S4", "P-U7", "10:00:01.000", "SVC_A"));
    assertTrue(rows().isEmpty(), "an unpaired response is not counted");
    service.ingest(req("S4", "P-U8", "10:00:00.000", "SVC_A"));

    PrimitiveCountDto row = rows().get(0);
    assertEquals(1, row.getRequests());
    assertEquals(1, row.getResponses());
    assertEquals(0, row.getWarnings());
    assertEquals(0, calls());
  }

  @Test
  @TestTransaction
  void unansweredRequestIsFlaggedOnceAfterTheBudgetAndStillAcceptsALateResponse() {
    service.ingest(req("S5", "P-U9", "10:00:00.000", "SVC_B"));
    assertEquals(0, primitives.sweep(Instant.now().plusSeconds(5)), "inside the budget it is only pending");
    assertEquals(0, rows().get(0).getWarnings());

    assertEquals(1, primitives.sweep(Instant.now().plusSeconds(11)));
    assertEquals(1, rows().get(0).getWarnings());
    assertEquals(0, primitives.sweep(Instant.now().plusSeconds(20)), "a call is flagged once");
    assertEquals(1, rows().get(0).getWarnings());

    service.ingest(resp("S5", "P-U10", "10:05:00.000", "SVC_B"));
    PrimitiveCountDto row = rows().get(0);
    assertEquals(1, row.getResponses());
    assertEquals(1, row.getWarnings(), "the late answer is not a second warning");
  }

  @Test
  @TestTransaction
  void redeliveryAndRepeatedLegsDoNotChangeTheCounters() {
    service.ingest(req("S6", "P-U11", "10:00:00.000", "SVC_A"));
    assertEquals(ReIngestionService.Outcome.NOOP, service.ingest(req("S6", "P-U11", "10:00:00.000", "SVC_A")));
    assertFalse(primitives.apply(PrimitiveAdmission.admit(req("S6", "P-U12", "10:00:00.500", "SVC_A")),
        req("S6", "P-U12", "10:00:00.500", "SVC_A"), Instant.parse("2024-06-15T08:00:00.500Z"), Instant.now()));
    assertEquals(1, rows().get(0).getRequests());
  }

  @Test
  @TestTransaction
  void splitsByServiceIdentifierAndPublishesAnAggregateOnlyOutboxRow() {
    service.ingest(req("S7", "P-U13", "10:00:00.000", "SVC_A"));
    service.ingest(req("S8", "P-U14", "10:00:00.000", "SVC_B"));
    service.ingest(req("S9", "P-U15", "10:00:00.000", null));
    service.ingest(resp("S8", "P-U16", "10:00:01.000", "SVC_B"));

    List<PrimitiveCountDto> rows = rows();
    assertEquals(3, rows.size());
    assertEquals(null, rows.get(0).getServiceIdentifier());
    assertEquals(1, rows.stream().filter(r -> "SVC_B".equals(r.getServiceIdentifier()))
        .findFirst().orElseThrow().getResponses());

    List<OutboxEntity> outbox = repository.unpublishedOutbox(50);
    List<OutboxEntity> primitiveRows = outbox.stream().filter(o -> o.getPrimitiveChanges() != null).toList();
    assertEquals(4, primitiveRows.size());
    assertTrue(primitiveRows.stream().allMatch(o -> o.getPaFiscalCode() == null && o.getPaymentChanges() == null));
    for (int i = 1; i < primitiveRows.size(); i++) {
      assertEquals(primitiveRows.get(i - 1).getAggregateRevision() + 1, primitiveRows.get(i).getAggregateRevision(),
          "every counted event bumps the aggregate revision by one");
    }
  }

  @Test
  @TestTransaction
  void detailsEachServiceByPrimitiveKeepingTheOriginalName() {
    service.ingest(req("S13", "P-U22", "10:00:00.000", "SVC_A"));
    service.ingest(resp("S13", "P-U23", "10:00:01.000", "SVC_A"));
    service.ingest(leg("REQ", "S14", "verifyPaymentNotice", "P-U24", "10:00:00.000", "SVC_A"));
    service.ingest(leg("REQ", "S15", "verifyPaymentNotice", "P-U25", "10:00:00.200", "SVC_A"));
    service.ingest(leg("RESP", "S15", "verifyPaymentNotice", "P-U26", "10:00:11.000", "SVC_A"));

    List<PrimitiveCountDto> rows = rows();
    assertEquals(List.of("activatePaymentNotice", "verifyPaymentNotice"),
        rows.stream().map(PrimitiveCountDto::getPrimitive).toList());
    PrimitiveCountDto verify = rows.get(1);
    assertEquals(2, verify.getRequests());
    assertEquals(1, verify.getResponses());
    assertEquals(1, verify.getWarnings(), "S15 answered after 10.8 s");
    assertEquals(3, sum(rows, PrimitiveCountDto::getRequests));
    assertEquals(0, rows.get(0).getWarnings());
  }

  @Test
  @TestTransaction
  void interfaceEventsWithoutACorrelationKeyAreDropped() {
    ReMessage noSession = req(null, "P-U17", "10:00:00.000", "SVC_A");
    assertEquals(ReIngestionService.Outcome.DROPPED, service.ingest(noSession));
    ReMessage internal = req("S10", "P-U18", "10:00:00.000", "SVC_A");
    internal.setSottoTipoEvento("INTERN");
    assertEquals(ReIngestionService.Outcome.DROPPED, service.ingest(internal));
    assertTrue(rows().isEmpty());
  }

  @Test
  @TestTransaction
  void overviewSnapshotExposesPrimitivesAndAppliesTheServiceFilter() {
    service.ingest(req("S11", "P-U19", "10:00:00.000", "SVC_A"));
    service.ingest(resp("S11", "P-U20", "10:00:01.000", "SVC_A"));
    service.ingest(req("S12", "P-U21", "10:00:00.000", "SVC_B"));
    em.flush();
    Map<String, String> window = Map.of("from", "2024-06-15T00:00:00.000Z", "to", "2024-06-16T00:00:00.000Z");

    var overview = observability.overview(Caller.implicit(), window);
    assertEquals(2, overview.getPrimitives().size());
    assertEquals(2, overview.getPrimitives().stream().mapToLong(PrimitiveCountDto::getRequests).sum());

    Map<String, String> onlyB = new java.util.HashMap<>(window);
    onlyB.put("serviceIdentifier", "SVC_B");
    var filtered = observability.overview(Caller.implicit(), onlyB).getPrimitives();
    assertEquals(1, filtered.size());
    assertEquals(0, filtered.get(0).getResponses());
  }

  @Test
  @TestTransaction
  void liveOverviewCountsRequestsAfterTheWindowEndAndAClosedWindowDoesNot() {
    service.ingest(req("S16", "P-U27", "10:00:00.000", "SVC_A"));
    em.flush();
    // The window ends before the request: only a live (rolling) window still counts it.
    Map<String, String> closed = Map.of("from", "2024-06-15T00:00:00.000Z", "to", "2024-06-15T06:00:00.000Z");
    assertTrue(observability.overview(Caller.implicit(), closed).getPrimitives().isEmpty());
    Map<String, String> live = new java.util.HashMap<>(closed);
    live.put("live", "true");
    var rows = observability.overview(Caller.implicit(), live).getPrimitives();
    assertEquals(1, rows.size());
    assertEquals(1, rows.get(0).getRequests());
  }

  @Test
  void liveWindowsAreOpenOnTheRightAndHonourTheLowerBound() {
    Instant minute = Instant.parse("2024-06-15T08:00:00Z");
    List<PrimitiveBucket> buckets = List.of(
        new PrimitiveBucket(minute.minusSeconds(3600), "SVC_A", "activatePaymentNotice", null, null, null, 9, 9, 0),
        new PrimitiveBucket(minute.plusSeconds(7200), "SVC_A", "activatePaymentNotice", null, null, null, 4, 3, 1));
    Instant to = minute.plusSeconds(60);
    assertTrue(PrimitiveLogic.rows(buckets, Filters.builder().build(), minute, to, null).isEmpty());
    var live = PrimitiveLogic.rows(buckets, Filters.builder().build(), minute, to, null, true);
    assertEquals(1, live.size());
    assertEquals(4, live.get(0).getRequests());
    assertEquals(1, live.get(0).getWarnings());
  }

  @Test
  void filtersWindowAndRestrictedCallersApplyToCountersAndDeltas() {
    Instant minute = Instant.parse("2024-06-15T08:00:00Z");
    List<PrimitiveBucket> buckets = List.of(
        new PrimitiveBucket(minute, "SVC_A", "activatePaymentNotice", "PSP1", "CH1", "ST1", 5, 4, 1),
        new PrimitiveBucket(minute, "SVC_B", "verifyPaymentNotice", "PSP2", "CH1", null, 3, 3, 0),
        new PrimitiveBucket(minute, null, "paGetPayment", null, null, null, 2, 0, 0),
        new PrimitiveBucket(minute.plusSeconds(3600), "SVC_A", "activatePaymentNotice", "PSP1", "CH1", "ST1", 7, 7, 0),
        new PrimitiveBucket(minute, "SVC_A", "activatePaymentNotice", "PSP1", "CH1", "ST1", -1, 0, 0),
        new PrimitiveBucket(minute, "SVC_A", "pspNotifyPayment", "PSP1", "CH1", "ST1", 2, 2, 0));
    Instant to = minute.plusSeconds(600);

    List<PrimitiveCountDto> all = PrimitiveLogic.rows(buckets, Filters.builder().build(), minute, to, null);
    assertEquals(java.util.Arrays.asList(null, "SVC_A", "SVC_A", "SVC_B"),
        all.stream().map(PrimitiveCountDto::getServiceIdentifier).toList());
    assertEquals(List.of("paGetPayment", "activatePaymentNotice", "pspNotifyPayment", "verifyPaymentNotice"),
        all.stream().map(PrimitiveCountDto::getPrimitive).toList(), "one row per service and primitive");
    PrimitiveCountDto a = all.get(1);
    assertEquals(4, a.getRequests(), "signed differences are summed and out-of-window minutes are excluded");

    assertEquals(1, PrimitiveLogic.rows(buckets, Filters.builder().psp("PSP2").build(), minute, to, null).size());
    assertEquals(2, PrimitiveLogic.rows(buckets, Filters.builder().serviceIdentifier("SVC_A").build(), minute, to, null).size());
    assertEquals(1, PrimitiveLogic.rows(buckets, Filters.builder().serviceIdentifier(Filters.UNKNOWN).build(),
        minute, to, null).size());
    assertEquals(2, PrimitiveLogic.rows(buckets, Filters.builder().stazione("ST1").build(), minute, to, null).size());
    assertTrue(PrimitiveLogic.rows(buckets, Filters.builder().build(), minute, to, "00000000001").isEmpty(),
        "a creditor-restricted caller gets no primitives");
    assertEquals(4, PrimitiveLogic.rows(buckets, Filters.builder().build(), minute.plusSeconds(30), to, null).size(),
        "a window starting mid-minute still includes that minute");
  }

  private static ReMessage described(ReMessage m, String pa, String canale, String stazione) {
    m.setIdDominio(pa);
    m.setCanale(canale);
    m.setStazione(stazione);
    return m;
  }

  private List<PrimitiveWarningDto> warnings(String primitive, Filters filters, boolean live, String allowedPa) {
    em.flush();
    return PrimitiveLogic.warnings(repository.primitiveWarnings(primitive, FROM, TO), primitive, filters, FROM, TO,
        allowedPa, live);
  }

  private List<PrimitiveWarningDto> warnings() {
    return warnings("activatePaymentNotice", Filters.builder().build(), false, null);
  }

  @Test
  @TestTransaction
  void anUnansweredRequestKeepsItsPspChannelCreditorStationAndTimeOnceItIsAWarning() {
    service.ingest(described(req("W1", "P-W1", "10:00:00.000", "SVC_A"), "00000000001", "CH1", "ST1"));
    assertTrue(warnings().isEmpty(), "a request still within its budget is only pending");
    assertEquals(1, primitives.sweep(Instant.now().plusSeconds(11)));
    primitives.sweep(Instant.now().plusSeconds(20));

    List<PrimitiveWarningDto> list = warnings();
    assertEquals(1, list.size(), "a call is recorded once");
    PrimitiveWarningDto warning = list.get(0);
    assertEquals("2024-06-15T08:00:00.000Z", warning.getCalledAt());
    assertEquals("PSP1", warning.getPsp());
    assertEquals("CH1", warning.getCanale());
    assertEquals("00000000001", warning.getPaFiscalCode());
    assertEquals("ST1", warning.getStazione());
    assertEquals(sum(rows(), PrimitiveCountDto::getWarnings), list.size(), "the list agrees with the counter");

    service.ingest(resp("W1", "P-W2", "10:05:00.000", "SVC_A"));
    assertEquals(1, warnings().size(), "the late answer is not a second warning");
  }

  @Test
  @TestTransaction
  void aLateResponseIsRecordedWithTheAttributesOfItsRequest() {
    service.ingest(described(req("W2", "P-W3", "10:00:00.000", "SVC_A"), "00000000002", "CH2", "ST2"));
    service.ingest(resp("W2", "P-W4", "10:00:10.001", "SVC_A"));
    service.ingest(described(req("W3", "P-W5", "10:00:00.000", "SVC_A"), "00000000003", "CH3", "ST3"));
    service.ingest(resp("W3", "P-W6", "10:00:09.000", "SVC_A"));

    List<PrimitiveWarningDto> list = warnings();
    assertEquals(1, list.size(), "an answer within ten seconds is not a warning");
    assertEquals("00000000002", list.get(0).getPaFiscalCode());
    assertEquals("ST2", list.get(0).getStazione());
  }

  @Test
  @TestTransaction
  void warningsAreNewestFirstAndFilteredLikeTheCounters() {
    service.ingest(described(req("W4", "P-W7", "10:00:00.000", "SVC_A"), "00000000001", "CH1", "ST1"));
    service.ingest(described(req("W5", "P-W8", "10:30:00.000", "SVC_B"), "00000000002", "CH2", null));
    service.ingest(req("W6", "P-W9", "11:00:00.000", "SVC_A"));
    primitives.sweep(Instant.now().plusSeconds(60));

    assertEquals(List.of("2024-06-15T09:00:00.000Z", "2024-06-15T08:30:00.000Z", "2024-06-15T08:00:00.000Z"),
        warnings().stream().map(PrimitiveWarningDto::getCalledAt).toList());
    PrimitiveWarningDto bare = warnings().get(0);
    assertEquals(null, bare.getPaFiscalCode(), "a missing creditor is exposed as null");
    assertEquals(null, bare.getCanale());
    assertEquals(null, bare.getStazione());
    assertEquals(2, warnings("activatePaymentNotice", Filters.builder().serviceIdentifier("SVC_A").build(), false, null).size());
    assertEquals(1, warnings("activatePaymentNotice", Filters.builder().stazione(Filters.UNKNOWN).build(), false, null)
        .stream().filter(w -> w.getCalledAt().startsWith("2024-06-15T08:30")).count());
    assertEquals(1, warnings("activatePaymentNotice", Filters.builder().canale("CH2").build(), false, null).size());
    assertTrue(warnings("verifyPaymentNotice", Filters.builder().build(), false, null).isEmpty());
    assertTrue(warnings("activatePaymentNotice", Filters.builder().build(), false, "00000000001").isEmpty(),
        "a creditor-restricted caller gets nothing, like the counters");
  }

  @Test
  void aLiveWindowListsWarningsAfterItsEndAndAClosedOneDoesNot() {
    Instant minute = Instant.parse("2024-06-15T08:00:00Z");
    List<PrimitiveWarningRow> calls = List.of(
        new PrimitiveWarningRow(minute.plusSeconds(7200), "SVC_A", "activatePaymentNotice", "PSP1", "CH1", "00000000001", "ST1"));
    Instant to = minute.plusSeconds(60);
    assertTrue(PrimitiveLogic.warnings(calls, "activatePaymentNotice", Filters.builder().build(), minute, to, null, false)
        .isEmpty());
    assertEquals(1, PrimitiveLogic.warnings(calls, "activatePaymentNotice", Filters.builder().build(), minute, to, null, true)
        .size());
  }

  @Test
  @TestTransaction
  void expiredWarningsFollowTheProjectionRetention() {
    service.ingest(described(req("W7", "P-W10", "10:00:00.000", "SVC_A"), "00000000001", "CH1", "ST1"));
    primitives.sweep(Instant.now().plusSeconds(60));
    assertEquals(1, warnings().size());

    repository.sweepExpired(Instant.now(), Instant.parse("2024-06-16T00:00:00Z"));
    em.clear();
    assertTrue(warnings().isEmpty());
  }

  private static ReMessage withParties(ReMessage m, String fruitore, String erogatore) {
    m.setFruitore(fruitore);
    m.setErogatore(erogatore);
    return m;
  }

  private static java.util.Map<String, String> directions(List<PrimitiveCountDto> rows) {
    return rows.stream().collect(java.util.stream.Collectors.toMap(
        r -> r.getServiceIdentifier() + "/" + r.getPrimitive(), PrimitiveCountDto::getDirection));
  }

  @Test
  @TestTransaction
  void directionComesFromTheRePartiesOfTheRequestAndNeverFromThePrimitiveName() {
    service.ingest(withParties(leg("REQ", "D1", "verifyPaymentNotice", "P-D1", "10:00:00.000", "SVC_A"),
        "02654890025", "NodoDeiPagamentiSPC"));
    service.ingest(withParties(leg("REQ", "D2", "paVerifyPaymentNotice", "P-D2", "10:00:00.000", "SVC_A"),
        "NodoDeiPagamentiSPC", "02438750586"));
    service.ingest(withParties(leg("REQ", "D3", "cacheVersion", "P-D3", "10:00:00.000", "SVC_A"), null, null));
    // same name as D1 but the Nodo is not a party: stays unknown instead of being guessed from the name
    service.ingest(withParties(leg("REQ", "D4", "activatePaymentNotice", "P-D4", "10:00:00.000", "SVC_A"),
        "PSP_X", "PSP_Y"));

    assertEquals(java.util.Map.of(
        "SVC_A/verifyPaymentNotice", "INBOUND",
        "SVC_A/paVerifyPaymentNotice", "OUTBOUND",
        "SVC_A/cacheVersion", "UNKNOWN",
        "SVC_A/activatePaymentNotice", "UNKNOWN"), directions(rows()));
  }

  @Test
  @TestTransaction
  void theSamePrimitiveInDifferentDirectionsIsCountedSeparately() {
    service.ingest(withParties(leg("REQ", "D5", "closePayment", "P-D5", "10:00:00.000", "SVC_A"),
        "PM", "NodoDeiPagamentiSPC"));
    service.ingest(withParties(leg("REQ", "D6", "closePayment", "P-D6", "10:00:01.000", "SVC_A"),
        "NodoDeiPagamentiSPC", "PM"));

    List<PrimitiveCountDto> rows = rows();
    assertEquals(2, rows.size());
    assertEquals(List.of("INBOUND", "OUTBOUND"), rows.stream().map(PrimitiveCountDto::getDirection).sorted().toList());
    assertEquals(2, sum(rows, PrimitiveCountDto::getRequests));
  }

  @Test
  @TestTransaction
  void aRequestKeepsItsDirectionWhenItsResponseArrivedFirst() {
    service.ingest(withParties(leg("RESP", "D7", "sendPaymentOutcome", "P-D7", "10:00:01.000", "SVC_A"),
        null, null));
    service.ingest(withParties(leg("REQ", "D7", "sendPaymentOutcome", "P-D8", "10:00:00.000", "SVC_A"),
        "PSP_X", "NodoDeiPagamentiSPC"));

    PrimitiveCountDto row = rows().get(0);
    assertEquals("INBOUND", row.getDirection());
    assertEquals(1, row.getResponses());
  }

  @Test
  void bucketsKeepTheirDirectionThroughTheOutboxFormatAndOlderRowsAreUnknown() {
    Instant minute = Instant.parse("2026-11-04T10:00:00Z");
    PrimitiveBucket outbound = new PrimitiveBucket(minute, "SVC_A", "pspNotifyPayment", null, null, null, 1, 1, 0,
        PrimitiveDirection.OUTBOUND);
    List<java.util.Map<String, Object>> maps = PrimitiveBucket.toMaps(List.of(outbound));
    assertEquals("OUTBOUND", maps.get(0).get("direction"));
    assertEquals(outbound, PrimitiveBucket.fromMaps(maps).get(0));

    java.util.Map<String, Object> legacy = new java.util.LinkedHashMap<>(maps.get(0));
    legacy.remove("direction");
    assertEquals(PrimitiveDirection.UNKNOWN, PrimitiveBucket.fromMaps(List.of(legacy)).get(0).direction());
  }

  @Test
  void directionRuleHasTheNodoAsErogatoreInboundAndAsFruitoreOutbound() {
    assertEquals(PrimitiveDirection.INBOUND, PrimitiveDirection.of("PSP", "NodoDeiPagamentiSPC"));
    assertEquals(PrimitiveDirection.OUTBOUND, PrimitiveDirection.of(" nododeipagamentispc ", "PA"));
    assertEquals(PrimitiveDirection.INBOUND, PrimitiveDirection.of("NodoDeiPagamentiSPC", "NodoDeiPagamentiSPC"));
    assertEquals(PrimitiveDirection.UNKNOWN, PrimitiveDirection.of(null, null));
    assertEquals(PrimitiveDirection.UNKNOWN, PrimitiveDirection.parse("SIDEWAYS"));
  }
}
