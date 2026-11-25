package it.gov.pagopa.nodetsworker.observability.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.observability.dto.AnalyticsDto;
import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import it.gov.pagopa.nodetsworker.observability.ingestion.ReIngestionService;
import it.gov.pagopa.nodetsworker.observability.ingestion.ReMessage;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import it.gov.pagopa.nodetsworker.observability.repository.models.AttemptEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Activations with outcome OK versus sendPaymentOutcome requests, fed through the real ingestion on H2. */
@QuarkusTest
class ActivationsOutcomesTest {

  private static final String SVC = "SVC_ANALYTICS";
  private static final String PA = "90000000077";
  private static final ZoneId ROME = ZoneId.of("Europe/Rome");
  /** Recent day, so that the projection retention never applies. */
  private static final String DAY = LocalDate.now(ROME).minusDays(2).toString();
  private static final Caller FULL = new Caller("tester", "OPERATOR", null, true, false);

  @Inject
  ReIngestionService ingestion;

  @Inject
  ObservabilityService service;

  @Inject
  com.fasterxml.jackson.databind.ObjectMapper mapper;

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  private static ReMessage paying(String notice, String token, String tipo, String uniqueId, String time) {
    ReMessage m = new ReMessage();
    m.setEsito("CAMBIO_STATO");
    m.setStatus("payment_PAYING");
    m.setTipoEvento(tipo);
    m.setIdDominio(PA);
    m.setNoticeNumber(notice);
    m.setPaymentToken(token);
    m.setUniqueId(uniqueId);
    m.setDataOraEvento(DAY + "T" + time);
    m.setServiceIdentifier(SVC);
    m.setPsp("PSP_AN");
    return m;
  }

  private static ReMessage leg(String subType, String tipo, String session, String uniqueId, String time,
      String esito) {
    ReMessage m = new ReMessage();
    m.setCategoriaEvento("INTERFACCIA");
    m.setSottoTipoEvento(subType);
    m.setEsito(esito);
    m.setTipoEvento(tipo);
    m.setSessionId(session);
    m.setUniqueId(uniqueId);
    m.setDataOraEvento(DAY + "T" + time);
    m.setServiceIdentifier(SVC);
    m.setPsp("PSP_AN");
    return m;
  }

  private void spo(String tipo, String session, String time) {
    ingestion.ingest(leg("REQ", tipo, session, session + "-req", time, "RICEVUTA"));
    ingestion.ingest(leg("RESP", tipo, session, session + "-resp", time, "INVIATA"));
  }

  /** UTC ISO instant of a Europe/Rome wall-clock time of {@link #DAY}, as RE times are interpreted. */
  private static String utc(String romeTime) {
    return IsoTime.format(LocalDateTime.parse(DAY + "T" + romeTime).atZone(ROME).toInstant());
  }

  /** Query with Europe/Rome wall-clock bounds on {@link #DAY}. */
  private AnalyticsDto query(String from, String to, String... extra) {
    em.flush();
    Map<String, String> q = new HashMap<>();
    q.put("from", utc(from));
    q.put("to", utc(to));
    q.put("serviceIdentifier", SVC);
    for (int i = 0; i < extra.length; i += 2) {
      q.put(extra[i], extra[i + 1]);
    }
    return service.activationsOutcomes(FULL, q);
  }

  @Test
  @TestTransaction
  void countsActivationsOkPerVersionAndEveryOutcomeRequestOnce() {
    ingestion.ingest(paying("390000000000077001", "TA1", "activatePaymentNotice", "A1", "10:00:05.000"));
    ingestion.ingest(paying("390000000000077002", "TA2", "activatePaymentNoticeV2", "A2", "10:01:00.000"));
    ingestion.ingest(paying("390000000000077003", "TA3", "activatePaymentNoticeV2", "A3", "10:02:30.000"));
    // Duplicate RE delivery of the same PAYING event and of the same SPO request.
    ingestion.ingest(paying("390000000000077003", "TA3", "activatePaymentNoticeV2", "A3", "10:02:30.000"));
    // Activate primitive legs (also for a KO activate) are not activations: no token, no PAYING.
    ingestion.ingest(leg("REQ", "activatePaymentNotice", "KO1", "KO1-req", "10:03:00.000", "RICEVUTA"));
    ingestion.ingest(leg("RESP", "activatePaymentNotice", "KO1", "KO1-resp", "10:03:00.100", "INVIATA"));
    spo("sendPaymentOutcome", "SPO1", "10:04:00.000");
    spo("sendPaymentOutcomeV2", "SPO2", "10:04:10.000");
    spo("sendPaymentOutcomeV2", "SPO3", "10:05:00.000");
    ingestion.ingest(leg("REQ", "sendPaymentOutcomeV2", "SPO3", "SPO3-req", "10:05:00.000", "RICEVUTA"));

    AnalyticsDto dto = query("10:00:00", "11:00:00");
    assertEquals(3, dto.getActivatePaymentNoticeOk().getTotal());
    assertEquals(1, dto.getActivatePaymentNoticeOk().getActivatePaymentNotice());
    assertEquals(2, dto.getActivatePaymentNoticeOk().getActivatePaymentNoticeV2());
    assertEquals(3, dto.getSendPaymentOutcome().getTotal());
    assertEquals(1, dto.getSendPaymentOutcome().getSendPaymentOutcome());
    assertEquals(2, dto.getSendPaymentOutcome().getSendPaymentOutcomeV2());
    assertEquals(0, dto.getDifference());
  }

  @Test
  @TestTransaction
  void ignoresPayingNotObservedOnAnActivateSourceEvent() {
    ingestion.ingest(paying("390000000000077011", "TB1", "activatePaymentNotice", "B1", "10:00:00.000"));
    ingestion.ingest(paying("390000000000077012", "TB2", "pspNotifyPayment", "B2", "10:00:00.000"));
    ingestion.ingest(paying("390000000000077013", "TB3", "activatePaymentNoticeV2", "B3", "10:00:00.000"));
    em.flush();
    AttemptEntity inferred = em.createQuery("SELECT a FROM AttemptEntity a WHERE a.paymentToken = 'TB3'",
        AttemptEntity.class).getSingleResult();
    inferred.getTimeline().get(0).setTimestampBasis(TimestampBasis.INFERRED);

    AnalyticsDto dto = query("10:00:00", "10:30:00");
    assertEquals(1, dto.getActivatePaymentNoticeOk().getTotal());
    assertEquals(1, dto.getActivatePaymentNoticeOk().getActivatePaymentNotice());
  }

  @Test
  @TestTransaction
  void bothCountersShareTheMinuteAlignedWindowAndFilters() {
    ingestion.ingest(paying("390000000000077021", "TC1", "activatePaymentNotice", "C1", "09:59:59.999"));
    ingestion.ingest(paying("390000000000077022", "TC2", "activatePaymentNotice", "C2", "10:00:00.000"));
    ingestion.ingest(paying("390000000000077023", "TC3", "activatePaymentNotice", "C3", "10:10:59.999"));
    ingestion.ingest(paying("390000000000077024", "TC4", "activatePaymentNotice", "C4", "10:11:00.000"));
    spo("sendPaymentOutcome", "SC1", "09:59:59.999");
    spo("sendPaymentOutcome", "SC2", "10:00:00.000");
    spo("sendPaymentOutcome", "SC3", "10:10:59.999");
    spo("sendPaymentOutcome", "SC4", "10:11:00.000");

    // RE times are Europe/Rome; the API window is UTC, floored/ceiled to the minute (to exclusive).
    AnalyticsDto dto = query("10:00:30", "10:10:30");
    assertEquals(utc("10:00:00"), dto.getWindow().getFrom());
    assertEquals(utc("10:11:00"), dto.getWindow().getTo());
    assertEquals(2, dto.getActivatePaymentNoticeOk().getTotal());
    assertEquals(2, dto.getSendPaymentOutcome().getTotal());

    AnalyticsDto live = query("10:00:00", "10:05:00", "live", "true");
    assertNull(live.getWindow().getTo());
    assertEquals(3, live.getActivatePaymentNoticeOk().getTotal());
    assertEquals(3, live.getSendPaymentOutcome().getTotal());

    AnalyticsDto otherPsp = query("09:00:00", "11:00:00", "psp", "PSP_OTHER");
    assertEquals(0, otherPsp.getActivatePaymentNoticeOk().getTotal());
    assertEquals(0, otherPsp.getSendPaymentOutcome().getTotal());
    AnalyticsDto noCanale = query("09:00:00", "11:00:00", "canale", "~unknown");
    assertEquals(4, noCanale.getActivatePaymentNoticeOk().getTotal());
    assertEquals(4, noCanale.getSendPaymentOutcome().getTotal());
  }

  private static ReMessage cancelled(String notice, String token, String tipo, String uniqueId, String time) {
    ReMessage m = paying(notice, token, tipo, uniqueId, time);
    m.setStatus("payment_CANCELLED");
    return m;
  }

  private static ReMessage state(String status, String notice, String token, String tipo, String uniqueId,
      String time) {
    ReMessage m = paying(notice, token, tipo, uniqueId, time);
    m.setStatus(status);
    return m;
  }

  @Test
  @TestTransaction
  void breaksDownEveryActivatedTokenByLastObservedStateAndCause() {
    // Settled by an accepted SPO (OK -> PAID, KO -> FAILED): acceptedOutcome.
    ingestion.ingest(paying("390000000000077041", "TE1", "activatePaymentNotice", "E1", "10:00:00.000"));
    ingestion.ingest(state("payment_PAID", "390000000000077041", "TE1", "sendPaymentOutcome", "E1p", "10:01:00.000"));
    ingestion.ingest(paying("390000000000077042", "TE2", "activatePaymentNoticeV2", "E2", "10:00:10.000"));
    ingestion.ingest(state("payment_FAILED", "390000000000077042", "TE2", "sendPaymentOutcomeV2", "E2f", "10:01:00.000"));
    // One V2 request settling a two-token cart: one request, two settled tokens.
    ingestion.ingest(paying("390000000000077043", "TE3", "activatePaymentNoticeV2", "E3", "10:00:20.000"));
    ingestion.ingest(paying("390000000000077044", "TE4", "activatePaymentNoticeV2", "E4", "10:00:30.000"));
    ingestion.ingest(state("payment_PAID", "390000000000077043", "TE3", "sendPaymentOutcomeV2", "E3p", "10:02:00.000"));
    ingestion.ingest(state("payment_PAID", "390000000000077044", "TE4", "sendPaymentOutcomeV2", "E4p", "10:02:00.000"));
    spo("sendPaymentOutcome", "SE1", "10:01:00.000");
    spo("sendPaymentOutcomeV2", "SE2", "10:01:00.000");
    spo("sendPaymentOutcomeV2", "SE3", "10:02:00.000");
    // V1 job after the window end, then a late SPO rejected (PPT_TOKEN_SCADUTO): no state change,
    // the request is counted but the token stays without an accepted outcome.
    ingestion.ingest(paying("390000000000077045", "TE5", "activatePaymentNotice", "E5", "10:03:00.000"));
    ingestion.ingest(state("payment_CANCELLED", "390000000000077045", "TE5", "mod3Cancel", "E5c", "11:15:00.000"));
    spo("sendPaymentOutcome", "SE5", "11:20:00.000");
    // V2 job, delivered twice: counted once.
    ingestion.ingest(paying("390000000000077046", "TE6", "activatePaymentNoticeV2", "E6", "10:04:00.000"));
    ingestion.ingest(state("payment_CANCELLED", "390000000000077046", "TE6", "mod3CancelV2", "E6c", "10:34:00.000"));
    ingestion.ingest(state("payment_CANCELLED", "390000000000077046", "TE6", "mod3CancelV2", "E6c", "10:34:00.000"));
    // Cancelled by a negative closePayment: concluded otherwise.
    ingestion.ingest(paying("390000000000077047", "TE7", "activatePaymentNoticeV2", "E7", "10:05:00.000"));
    ingestion.ingest(state("payment_CANCELLED", "390000000000077047", "TE7", "closePaymentV2", "E7c", "10:06:00.000"));
    // Still waiting for the outcome or the job.
    ingestion.ingest(paying("390000000000077048", "TE8", "activatePaymentNotice", "E8", "10:20:00.000"));
    // Cancelled by the job, but activated outside the window.
    ingestion.ingest(paying("390000000000077049", "TE9", "activatePaymentNotice", "E9", "11:30:00.000"));
    ingestion.ingest(state("payment_CANCELLED", "390000000000077049", "TE9", "mod3Cancel", "E9c", "12:00:00.000"));

    AnalyticsDto dto = query("10:00:00", "10:30:00");
    assertEquals(8, dto.getActivatePaymentNoticeOk().getTotal());
    assertEquals(3, dto.getSendPaymentOutcome().getTotal());
    assertEquals(-5, dto.getDifference());
    AnalyticsDto.TokensDto t = dto.getTokens();
    assertEquals(dto.getActivatePaymentNoticeOk().getTotal(), t.getTotal());
    assertEquals(t.getTotal(), t.getAcceptedOutcome().getTotal() + t.getCancelledByMod3Cancel().getTotal()
        + t.getOtherCancelled().getTotal() + t.getNonTerminal().getTotal() + t.getOtherTerminal().getTotal());
    assertEquals(4, t.getAcceptedOutcome().getTotal());
    assertEquals(1, t.getAcceptedOutcome().getSendPaymentOutcome());
    assertEquals(3, t.getAcceptedOutcome().getSendPaymentOutcomeV2());
    assertEquals(2, t.getCancelledByMod3Cancel().getTotal());
    assertEquals(1, t.getCancelledByMod3Cancel().getMod3Cancel());
    assertEquals(1, t.getCancelledByMod3Cancel().getMod3CancelV2());
    assertEquals(1, t.getOtherCancelled().getTotal());
    assertEquals(List.of(new AnalyticsDto.Bucket(null, "closePaymentV2", 1)), t.getOtherCancelled().getByCause());
    assertEquals(1, t.getNonTerminal().getTotal());
    assertEquals(List.of(new AnalyticsDto.StateCount("PAYING", 1)), t.getNonTerminal().getByState());
    assertEquals(0, t.getOtherTerminal().getTotal());
    assertBucketsSumToTotals(t);

    assertEquals(0, query("10:00:00", "10:30:00", "psp", "PSP_OTHER").getTokens().getTotal());
  }

  @Test
  @TestTransaction
  void sortsBucketsByCountDescending() {
    ingestion.ingest(paying("390000000000077061", "TG1", "activatePaymentNotice", "G1", "10:00:00.000"));
    ingestion.ingest(paying("390000000000077062", "TG2", "activatePaymentNotice", "G2", "10:00:01.000"));
    ingestion.ingest(state("payment_CANCELLED", "390000000000077062", "TG2", "nodoInviaRPT", "G2c", "10:01:00.000"));
    ingestion.ingest(paying("390000000000077063", "TG3", "activatePaymentNotice", "G3", "10:00:02.000"));
    ingestion.ingest(state("payment_CANCELLED", "390000000000077063", "TG3", "closePaymentV2", "G3c", "10:01:00.000"));
    ingestion.ingest(paying("390000000000077064", "TG4", "activatePaymentNotice", "G4", "10:00:03.000"));
    ingestion.ingest(state("payment_CANCELLED", "390000000000077064", "TG4", "closePaymentV2", "G4c", "10:01:00.000"));

    var byCause = query("10:00:00", "10:30:00").getTokens().getOtherCancelled().getByCause();
    assertEquals(List.of("closePaymentV2", "nodoInviaRPT"), byCause.stream().map(AnalyticsDto.Bucket::getCause).toList());
    assertEquals(List.of(2L, 1L), byCause.stream().map(AnalyticsDto.Bucket::getCount).toList());
  }

  /** Asserts the invariant the frontend validates: every array sums to its category total, null cause included. */
  private static void assertBucketsSumToTotals(AnalyticsDto.TokensDto t) {
    assertEquals(t.getOtherCancelled().getTotal(),
        t.getOtherCancelled().getByCause().stream().mapToLong(AnalyticsDto.Bucket::getCount).sum());
    assertEquals(t.getNonTerminal().getTotal(),
        t.getNonTerminal().getByState().stream().mapToLong(AnalyticsDto.StateCount::getCount).sum());
    assertEquals(t.getOtherTerminal().getTotal(),
        t.getOtherTerminal().getByState().stream().mapToLong(AnalyticsDto.Bucket::getCount).sum());
    assertEquals(t.getAcceptedOutcome().getTotal(),
        t.getAcceptedOutcome().getSendPaymentOutcome() + t.getAcceptedOutcome().getSendPaymentOutcomeV2());
    assertEquals(t.getCancelledByMod3Cancel().getTotal(),
        t.getCancelledByMod3Cancel().getMod3Cancel() + t.getCancelledByMod3Cancel().getMod3CancelV2());
    assertEquals(t.getTotal(), t.getAcceptedOutcome().getTotal() + t.getCancelledByMod3Cancel().getTotal()
        + t.getOtherCancelled().getTotal() + t.getNonTerminal().getTotal() + t.getOtherTerminal().getTotal());
  }

  private static ProjectionRepository.TokenGroup group(int version, String state, String cause, String psp,
      long n) {
    return new ProjectionRepository.TokenGroup(version, state, cause, SVC, psp, null, null, n);
  }

  @Test
  void bucketsSumToTheirTotalsIncludingAnUnrecordedCause() {
    var t = ObservabilityService.tokenBreakdown(List.of(
        group(1, "NOTIFIED", "sendPaymentOutcome", "P1", 4),
        group(2, "PAID", "sendPaymentOutcomeV2", "P2", 3),
        group(0, "CANCELLED", "mod3CancelV1", "P1", 2),
        group(0, "CANCELLED", "MOD3CANCELV2", "P2", 5),
        group(0, "CANCELLED", null, "P1", 2),
        group(0, "CANCELLED", null, "P2", 1),
        group(0, "CANCELLED", "closePayment-v2", "P1", 6),
        group(0, "PAYING", null, "P1", 1),
        group(0, "PAYING", "activatePaymentNotice", "P1", 7),
        group(0, "PAYING", "activatePaymentNotice", "P2", 2),
        group(0, "FAILED", null, "P1", 1),
        group(0, "NOTIFIED", "paSendRT", "P2", 2)), QuerySupport.filters(Map.of()));
    assertBucketsSumToTotals(t);
    assertEquals(36, t.getTotal());
    assertEquals(List.of(new AnalyticsDto.Bucket(null, "closePayment-v2", 6), new AnalyticsDto.Bucket(null, null, 3)),
        t.getOtherCancelled().getByCause());
    // One item per state, whatever the activation that led to it.
    assertEquals(List.of(new AnalyticsDto.StateCount("PAYING", 10)), t.getNonTerminal().getByState());
    assertEquals(3, t.getOtherTerminal().getTotal());
    assertEquals(2, t.getCancelledByMod3Cancel().getMod3Cancel());
    assertEquals(5, t.getCancelledByMod3Cancel().getMod3CancelV2());

    var p2 = ObservabilityService.tokenBreakdown(List.of(group(0, "CANCELLED", null, "P2", 1),
        group(0, "CANCELLED", null, "P1", 2)), QuerySupport.filters(Map.of("psp", "P2")));
    assertBucketsSumToTotals(p2);
    assertEquals(List.of(new AnalyticsDto.Bucket(null, null, 1)), p2.getOtherCancelled().getByCause());
  }

  @Test
  void bucketSerializesAnUnrecordedCauseAsNullAndOmitsTheStateOfCauseBuckets() throws Exception {
    assertEquals("{\"cause\":null,\"count\":3}", mapper.writeValueAsString(new AnalyticsDto.Bucket(null, null, 3)));
    assertEquals("{\"state\":\"PAYING\",\"cause\":\"x\",\"count\":1}",
        mapper.writeValueAsString(new AnalyticsDto.Bucket("PAYING", "x", 1)));
  }

  @Test
  @TestTransaction
  void neverAssertsCompletenessAndRefusesTheBreakdownBeyondRetention() {
    ingestion.ingest(paying("390000000000077051", "TF1", "activatePaymentNotice", "F1", "10:00:00.000"));

    AnalyticsDto dto = query("10:00:00", "10:30:00");
    assertEquals("UNKNOWN", dto.getCoverage().getStatus());
    assertEquals(List.of("NO_INGESTION_WATERMARK"), dto.getCoverage().getReasons());
    // Latest event seen by this instance (other data may be later): informative, not a watermark.
    assertTrue(dto.getCoverage().getLastObservedEventAt().compareTo(utc("10:00:00")) >= 0);
    assertEquals(1, dto.getTokens().getTotal());

    Instant old = Instant.now().minus(61, ChronoUnit.DAYS);
    AnalyticsDto expired = service.activationsOutcomes(FULL, Map.of("from", IsoTime.format(old),
        "to", IsoTime.format(old.plus(1, ChronoUnit.HOURS))));
    assertEquals("UNAVAILABLE", expired.getCoverage().getStatus());
    assertTrue(expired.getCoverage().getReasons().contains("RETENTION"));
    assertNull(expired.getTokens());
  }

  @Test
  void restrictedCallerIsRefused() {
    Caller pa = new Caller("ec", "CREDITOR", PA, false, false);
    assertThrows(ObservabilityException.class, () -> service.activationsOutcomes(pa, Map.of()));
  }
}
