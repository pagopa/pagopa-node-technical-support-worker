package it.gov.pagopa.nodetsworker.observability.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import it.gov.pagopa.nodetsworker.observability.domain.DomainAttempt;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.DomainRecipient;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.repository.models.AttemptEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.OutboxEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PaymentEntity;
import it.gov.pagopa.nodetsworker.observability.service.ProjectionMapper;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;

/** End-to-end ingestion of RE events into the projection on H2 (spec §4.1–§4.3, acceptance §8). */
@QuarkusTest
class ReIngestionServiceTest {

  private static final String PA = "90000000001";

  @Inject
  ReIngestionService service;

  @Inject
  ProjectionRepository repository;

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  @Inject
  ObjectMapper objectMapper;

  @InjectSpy
  IngestionProjectionWriter writerSpy;

  private static ReMessage payment(String notice, String token, String bareState, String uniqueId,
      String dataOra) {
    ReMessage m = new ReMessage();
    m.setEsito("CAMBIO_STATO");
    m.setStatus("payment_" + bareState);
    m.setIdDominio(PA);
    m.setNoticeNumber(notice);
    m.setPaymentToken(token);
    m.setUniqueId(uniqueId);
    m.setDataOraEvento(dataOra);
    m.setServiceIdentifier("SVC_A");
    return m;
  }

  private static ReMessage position(String notice, String bareState, String uniqueId, String dataOra) {
    ReMessage m = new ReMessage();
    m.setEsito("CAMBIO_STATO");
    m.setStatus("position_" + bareState);
    m.setIdDominio(PA);
    m.setNoticeNumber(notice);
    m.setUniqueId(uniqueId);
    m.setDataOraEvento(dataOra);
    return m;
  }

  private static ReMessage recipient(String notice, String bareState, String uniqueId, String dataOra,
      String station) {
    ReMessage m = new ReMessage();
    m.setEsito("CAMBIO_STATO");
    m.setStatus("receipt_recipient_" + bareState);
    m.setIdDominio(PA);
    m.setNoticeNumber(notice);
    m.setUniqueId(uniqueId);
    m.setDataOraEvento(dataOra);
    if (station != null) {
      m.setInfo("stazione_recipient=" + station);
    }
    return m;
  }

  private DomainPayment load(String notice) {
    return repository.loadVisible(null).stream()
        .filter(p -> PA.equals(p.getPaFiscalCode()) && notice.equals(p.getNoticeNumber()))
        .findFirst().orElse(null);
  }

  private long anomalyCount() {
    return em.createQuery("SELECT COUNT(a) FROM IngestionAnomalyEntity a", Long.class).getSingleResult();
  }

  @Test
  @TestTransaction
  void activationCreatesPaymentAttemptAndLogical() {
    String notice = "390000000000000001";
    ReIngestionService.Outcome o = service.ingest(payment(notice, "TKN1", "PAYING", "U1",
        "2024-06-15T10:00:00.000"));
    assertEquals(ReIngestionService.Outcome.APPLIED, o);

    DomainPayment p = load(notice);
    assertNotNull(p);
    assertNull(p.currentState());
    assertTrue(p.isTimelineIncomplete());
    assertEquals(1, p.getAttempts().size());
    DomainAttempt a = p.getAttempts().get(0);
    assertEquals("PAYING", a.currentState());
    assertEquals(TimestampBasis.SOURCE_EVENT, a.getStartTimestampBasis());
    assertNotNull(a.getAttemptStartedAt());
  }

  @Test
  @TestTransaction
  void attemptEventDoesNotInventLogicalPositionState() throws Exception {
    String notice = "390000000000000019";
    assertEquals(ReIngestionService.Outcome.APPLIED,
        service.ingest(payment(notice, "TKN19", "PAYING", "U19", "2024-06-15T10:00:00.000")));

    DomainPayment payment = load(notice);
    assertNotNull(payment);
    assertNull(payment.currentState());
    assertTrue(payment.getTimeline().isEmpty());
    assertTrue(payment.isTimelineIncomplete());
    assertEquals("PAYING", payment.getAttempts().get(0).currentState());

    assertEquals(ReIngestionService.Outcome.APPLIED,
        service.ingest(position(notice, "PAYING", "U19-position", "2024-06-15T10:00:01.000")));
    payment = load(notice);
    assertEquals("PAYING", payment.currentState());
    assertFalse(payment.isTimelineIncomplete());
    assertEquals(1, payment.getTimeline().size());
    var delta = objectMapper.readTree(repository.unpublishedOutbox(50).stream()
        .filter(row -> notice.equals(row.getNoticeNumber()))
        .reduce((first, second) -> second).orElseThrow().getPaymentChanges());
    assertTrue(delta.has("timelineIncomplete"));
    assertFalse(delta.get("timelineIncomplete").asBoolean());
  }

  @Test
  @TestTransaction
  void unsupportedStatusIsAnomalyWithoutProjectionOrRawToken() {
    String notice = "390000000000000020";
    long before = anomalyCount();
    assertEquals(ReIngestionService.Outcome.ANOMALY,
        service.ingest(payment(notice, "RAW-TOKEN-20", "UNKNOWN", "U20", "2024-06-15T10:00:00.000")));

    assertNull(load(notice));
    assertEquals(before + 1, anomalyCount());
    var anomaly = em.createQuery(
        "SELECT a FROM IngestionAnomalyEntity a WHERE a.uniqueId = :id",
        it.gov.pagopa.nodetsworker.observability.repository.models.IngestionAnomalyEntity.class)
        .setParameter("id", "U20").getSingleResult();
    assertNull(anomaly.getPaymentToken());
    assertFalse(anomaly.getDetail().contains("RAW-TOKEN-20"));
  }

  @Test
  @TestTransaction
  void timelineRetainsTipoEventoForRestAndOutbox() throws Exception {
    String notice = "390000000000000021";
    ReMessage event = payment(notice, "TKN21", "PAYING", "U21", "2024-06-15T10:00:00.000");
    event.setTipoEvento("activatePaymentNoticeV2");
    assertEquals(ReIngestionService.Outcome.APPLIED, service.ingest(event));

    DomainPayment payment = load(notice);
    assertEquals("activatePaymentNoticeV2", payment.getAttempts().get(0).getTimeline().get(0).getTrigger());
    assertEquals("activatePaymentNoticeV2",
        ProjectionMapper.toDetailDto(payment).getAttempts().get(0).getTimeline().get(0).getTrigger());
    var outbox = repository.unpublishedOutbox(50).stream()
        .filter(row -> notice.equals(row.getNoticeNumber())).findFirst().orElseThrow();
    var changes = objectMapper.readTree(outbox.getPaymentChanges());
    assertTrue(changes.at("/attemptChanges/0/transitions").isEmpty());
    assertEquals("activatePaymentNoticeV2",
        changes.at("/attemptChanges/0/upsert/timeline/0/trigger").asText());
    assertFalse(outbox.getPaymentChanges().contains("TKN21"));
  }

  @Test
  @TestTransaction
  void inferredAttemptTransitionsKeepTheirSupportingEventTrigger() throws Exception {
    String notice = "390000000000000028";
    ReMessage event = payment(notice, "TKN28", "PAYMENT_SENT", "U28", "2024-06-15T10:00:00.000");
    event.setTipoEvento("pspNotifyPaymentV2");
    assertEquals(ReIngestionService.Outcome.ANOMALY, service.ingest(event));

    DomainAttempt attempt = load(notice).getAttempts().get(0);
    assertEquals(List.of("PAYMENT_RESERVED", "PAYMENT_SENT"),
        attempt.getTimeline().stream().map(item -> item.getState()).toList());
    assertEquals(List.of("pspNotifyPaymentV2", "pspNotifyPaymentV2"),
        attempt.getTimeline().stream().map(item -> item.getTrigger()).toList());
    var changes = objectMapper.readTree(repository.unpublishedOutbox(50).stream()
        .filter(row -> notice.equals(row.getNoticeNumber())).findFirst().orElseThrow().getPaymentChanges());
    assertEquals("pspNotifyPaymentV2",
        changes.at("/attemptChanges/0/upsert/timeline/0/trigger").asText());
  }

  @Test
  @TestTransaction
  void positionTimelineRetainsItsOwnSourceTrigger() throws Exception {
    String notice = "390000000000000029";
    ReMessage event = position(notice, "PAYING", "U29", "2024-06-15T10:00:00.000");
    event.setTipoEvento("activatePaymentNoticeV2");
    assertEquals(ReIngestionService.Outcome.APPLIED, service.ingest(event));

    DomainPayment payment = load(notice);
    assertEquals("activatePaymentNoticeV2", payment.getTimeline().get(0).getTrigger());
    assertEquals("activatePaymentNoticeV2",
        ProjectionMapper.toDetailDto(payment).getTimeline().get(0).getTrigger());
    var changes = objectMapper.readTree(repository.unpublishedOutbox(50).stream()
        .filter(row -> notice.equals(row.getNoticeNumber())).findFirst().orElseThrow().getPaymentChanges());
    assertEquals("activatePaymentNoticeV2", changes.at("/logicalTransitions/0/trigger").asText());
  }

  @Test
  @TestTransaction
  void transportFallbackDeduplicatesWithoutExceedingInboxKeyLimit() {
    String notice = "390000000000000022";
    ReMessage event = payment(notice, "TKN22", "PAYING", null, "2024-06-15T10:00:00.000");
    event.setEventHub("h".repeat(120));
    event.setPartitionId("0");
    event.setSequenceNumber(Long.MAX_VALUE);

    assertEquals(ReIngestionService.Outcome.APPLIED, service.ingest(event));
    long revision = load(notice).getProjectionRevision();
    assertEquals(ReIngestionService.Outcome.NOOP, service.ingest(event));
    assertEquals(revision, load(notice).getProjectionRevision());

    String malformedIdNotice = "390000000000000030";
    ReMessage malformedId = payment(malformedIdNotice, "TKN30", "PAYING", "U".repeat(129),
        "2024-06-15T10:00:00.000");
    malformedId.setEventHub("hub");
    malformedId.setPartitionId("0");
    malformedId.setSequenceNumber(30L);
    assertEquals(ReIngestionService.Outcome.APPLIED, service.ingest(malformedId));
    assertEquals(ReIngestionService.Outcome.NOOP, service.ingest(malformedId));
  }

  @Test
  void failureAfterProjectionWriteRollsBackInboxProjectionAndOutbox() {
    String notice = "390000000000000031";
    doAnswer(invocation -> {
      invocation.callRealMethod();
      throw new IllegalStateException("forced failure after projection/outbox write");
    }).when(writerSpy).apply(any(), any(), any(), any(), anyLong());

    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
        () -> service.ingest(payment(notice, "TKN31", "PAYING", "U31", "2024-06-15T10:00:00.000")));

    assertNull(load(notice));
    assertEquals(0L, em.createQuery(
        "SELECT COUNT(i) FROM InboxEntity i WHERE i.uniqueId = :id", Long.class)
        .setParameter("id", "U31").getSingleResult());
    assertEquals(0L, em.createQuery(
        "SELECT COUNT(o) FROM OutboxEntity o WHERE o.noticeNumber = :notice", Long.class)
        .setParameter("notice", notice).getSingleResult());
  }

  @Test
  @TestTransaction
  void lateSourceTimeCorrectsInferredTransitionWithOriginalTriggerAndStableIndex() throws Exception {
    String notice = "390000000000000023";
    service.ingest(payment(notice, "TKN23", "PAYING", "U23a", "2024-06-15T10:00:00.000"));
    service.ingest(payment(notice, "TKN23", "PAYMENT_RESERVED", "U23b", "2024-06-15T10:01:00.000"));
    PaymentEntity entity = em.createQuery(
        "SELECT p FROM PaymentEntity p WHERE p.paFiscalCode = :pa AND p.noticeNumber = :notice",
        PaymentEntity.class).setParameter("pa", PA).setParameter("notice", notice).getSingleResult();
    AttemptEntity attempt = entity.getAttempts().get(0);
    attempt.getTimeline().get(0).setTimestampBasis(TimestampBasis.INFERRED);
    attempt.setStartTimestampBasis(TimestampBasis.INFERRED);
    var expiry = attempt.getExpiresAt();
    em.flush();

    ReMessage latePaying = payment(notice, "TKN23", "PAYING", "U23c", "2024-06-15T09:59:00.000");
    latePaying.setTipoEvento("activatePaymentNoticeV2");
    assertEquals(ReIngestionService.Outcome.APPLIED, service.ingest(latePaying));

    assertEquals("PAYMENT_RESERVED", attempt.getCurrentState());
    assertFalse(attempt.getExpiresAt().isAfter(expiry));
    var changes = objectMapper.readTree(repository.unpublishedOutbox(50).stream()
        .filter(row -> notice.equals(row.getNoticeNumber())).max(java.util.Comparator.comparingLong(
            OutboxEntity::getSequenceNo)).orElseThrow().getPaymentChanges());
    assertEquals(0, changes.at("/attemptChanges/0/transitionCorrections/0/index").asInt());
    assertEquals("PAYING",
        changes.at("/attemptChanges/0/transitionCorrections/0/transition/state").asText());
    assertEquals("activatePaymentNoticeV2",
        changes.at("/attemptChanges/0/transitionCorrections/0/transition/trigger").asText());
    assertEquals("SOURCE_EVENT",
        changes.at("/attemptChanges/0/transitionCorrections/0/transition/timestampBasis").asText());
  }

  @Test
  @TestTransaction
  void closeBeforeActivateDoesNotRegress() {
    // closePayment (PAYMENT_RESERVED) arrives first: the legacy PA OLD path means PAYING is unknown.
    String notice = "390000000000000002";
    service.ingest(payment(notice, "TKN2", "PAYMENT_RESERVED", "U2a", "2024-06-15T10:05:00.000"));
    DomainAttempt a = load(notice).getAttempts().get(0);
    assertEquals("PAYMENT_RESERVED", a.currentState());
    assertEquals(TimestampBasis.INFERRED, a.getStartTimestampBasis());

    // Late activation: no regression, but the estimated start is replaced by the real event time.
    ReIngestionService.Outcome o = service.ingest(payment(notice, "TKN2", "PAYING", "U2b",
        "2024-06-15T10:00:00.000"));
    assertEquals(ReIngestionService.Outcome.APPLIED, o);
    a = load(notice).getAttempts().get(0);
    assertEquals("PAYMENT_RESERVED", a.currentState()); // state not regressed
    assertEquals(TimestampBasis.SOURCE_EVENT, a.getStartTimestampBasis());
  }

  @Test
  @TestTransaction
  void reopenedPositionCanReceiveNewAttemptTokenWithoutReopeningOldAttempt() {
    String notice = "390000000000000026";
    service.ingest(position(notice, "PAYING", "U26a", "2024-06-15T10:00:00.000"));
    service.ingest(payment(notice, "TKN26-A", "PAYING", "U26b", "2024-06-15T10:00:01.000"));
    service.ingest(payment(notice, "TKN26-A", "CANCELLED", "U26c", "2024-06-15T10:01:00.000"));
    service.ingest(position(notice, "INSERTED", "U26d", "2024-06-15T10:02:00.000"));
    service.ingest(position(notice, "PAYING", "U26e", "2024-06-15T10:03:00.000"));
    assertEquals(ReIngestionService.Outcome.APPLIED,
        service.ingest(payment(notice, "TKN26-B", "PAYING", "U26f", "2024-06-15T10:03:01.000")));

    DomainPayment payment = load(notice);
    assertEquals("PAYING", payment.currentState());
    assertEquals(2, payment.getAttempts().size());
    assertEquals("CANCELLED", payment.getAttempts().stream()
        .filter(attempt -> "TKN26-A".equals(attempt.getPaymentToken())).findFirst().orElseThrow().currentState());
    assertEquals("PAYING", payment.getAttempts().stream()
        .filter(attempt -> "TKN26-B".equals(attempt.getPaymentToken())).findFirst().orElseThrow().currentState());
  }

  @Test
  @TestTransaction
  void duplicateIsIdempotent() {
    String notice = "390000000000000003";
    assertEquals(ReIngestionService.Outcome.APPLIED,
        service.ingest(payment(notice, "TKN3", "PAYING", "DUP", "2024-06-15T10:00:00.000")));
    long revAfterFirst = load(notice).getProjectionRevision();
    assertEquals(ReIngestionService.Outcome.NOOP,
        service.ingest(payment(notice, "TKN3", "PAYING", "DUP", "2024-06-15T10:00:00.000")));
    assertEquals(1, load(notice).getAttempts().size());
    assertEquals(revAfterFirst, load(notice).getProjectionRevision());
  }

  @Test
  @TestTransaction
  void functionalNoopDoesNotIncrementRevisionOrCreateOutbox() {
    String notice = "390000000000000025";
    service.ingest(payment(notice, "TKN25", "PAYING", "U25a", "2024-06-15T10:00:00.000"));
    long revision = load(notice).getProjectionRevision();
    long outboxRows = repository.unpublishedOutbox(100).stream()
        .filter(row -> notice.equals(row.getNoticeNumber())).count();

    assertEquals(ReIngestionService.Outcome.NOOP,
        service.ingest(payment(notice, "TKN25", "PAYING", "U25b", "2024-06-15T10:00:00.000")));
    assertEquals(revision, load(notice).getProjectionRevision());
    assertEquals(outboxRows, repository.unpublishedOutbox(100).stream()
        .filter(row -> notice.equals(row.getNoticeNumber())).count());
  }

  @Test
  @TestTransaction
  void missingEventTimeBecomesAnomalyItem() {
    String notice = "390000000000000004";
    assertEquals(ReIngestionService.Outcome.APPLIED,
        service.ingest(position(notice, "PAYING", "U4", null)));
    var timeline = load(notice).getTimeline();
    var last = timeline.get(timeline.size() - 1);
    assertEquals(TimestampBasis.ANOMALY, last.getTimestampBasis());
    assertNull(last.getTransitionAt());
  }

  @Test
  @TestTransaction
  void missingCorrelationKeyIsAnomalyWithoutProjection() {
    String notice = "390000000000000005";
    long before = anomalyCount();
    ReMessage m = payment(notice, null, "PAYING", "U5", "2024-06-15T10:00:00.000"); // no token
    assertEquals(ReIngestionService.Outcome.ANOMALY, service.ingest(m));
    assertEquals(before + 1, anomalyCount());
    assertNull(load(notice));
  }

  @Test
  @TestTransaction
  void malformedCorrelationKeyIsAnomalyWithoutProjection() {
    String notice = "390000000000000024";
    ReMessage event = payment(notice, "TOKEN-INVALID-KEY", "PAYING", "U24",
        "2024-06-15T10:00:00.000");
    event.setIdDominio("not-a-fiscal-code");

    assertEquals(ReIngestionService.Outcome.ANOMALY, service.ingest(event));
    assertNull(load(notice));
    var anomaly = em.createQuery(
        "SELECT a FROM IngestionAnomalyEntity a WHERE a.uniqueId = :id",
        it.gov.pagopa.nodetsworker.observability.repository.models.IngestionAnomalyEntity.class)
        .setParameter("id", "U24").getSingleResult();
    assertNull(anomaly.getPaFiscalCode());
    assertNull(anomaly.getPaymentToken());
    assertFalse(anomaly.getDetail().contains("TOKEN-INVALID-KEY"));
  }

  @Test
  @TestTransaction
  void oversizedAttemptTokenIsAnomalyWithoutPersistingTheToken() {
    String notice = "390000000000000027";
    ReMessage event = payment(notice, "T".repeat(129), "PAYING", "U27",
        "2024-06-15T10:00:00.000");

    assertEquals(ReIngestionService.Outcome.ANOMALY, service.ingest(event));
    assertNull(load(notice));
    var anomaly = em.createQuery(
        "SELECT a FROM IngestionAnomalyEntity a WHERE a.uniqueId = :id",
        it.gov.pagopa.nodetsworker.observability.repository.models.IngestionAnomalyEntity.class)
        .setParameter("id", "U27").getSingleResult();
    assertNull(anomaly.getPaymentToken());
  }

  @Test
  @TestTransaction
  void dimensionConflictIsAnomalyAndDoesNotOverwrite() {
    String notice = "390000000000000006";
    ReMessage first = payment(notice, "TKN6", "PAYING", "U6a", "2024-06-15T10:00:00.000");
    first.setPsp("ABI00001");
    service.ingest(first);

    long before = anomalyCount();
    ReMessage second = payment(notice, "TKN6", "PAYMENT_RESERVED", "U6b", "2024-06-15T10:01:00.000");
    second.setPsp("ABI00002"); // conflicting non-null value
    service.ingest(second);

    assertEquals(before + 1, anomalyCount());
    assertEquals("ABI00001", load(notice).getAttempts().get(0).getPsp()); // not overwritten
  }

  @Test
  @TestTransaction
  void receiptRecipientProjectsThirdEntityWithItsOwnTimeline() throws Exception {
    String notice = "390000000000000040";
    assertEquals(ReIngestionService.Outcome.APPLIED,
        service.ingest(recipient(notice, "NOTICE_GENERATED", "UR1", "2024-06-15T10:00:00.000", "STAZ-1")));

    DomainPayment p = load(notice);
    assertNotNull(p);
    assertNull(p.currentState()); // no position event observed
    assertTrue(p.getAttempts().isEmpty());
    assertEquals(1, p.getRecipients().size());
    DomainRecipient r = p.getRecipients().get(0);
    assertEquals("NOTICE_GENERATED", r.currentState());
    assertEquals("STAZ-1", r.getStazioneRecipient());

    // A later state for the same recipient advances its own timeline and emits recipientChanges.
    assertEquals(ReIngestionService.Outcome.APPLIED,
        service.ingest(recipient(notice, "NOTICE_SENT", "UR2", "2024-06-15T10:01:00.000", "STAZ-1")));
    p = load(notice);
    assertEquals(1, p.getRecipients().size());
    assertEquals("NOTICE_SENT", p.getRecipients().get(0).currentState());

    var changes = objectMapper.readTree(repository.unpublishedOutbox(50).stream()
        .filter(row -> notice.equals(row.getNoticeNumber()))
        .max(java.util.Comparator.comparingLong(OutboxEntity::getSequenceNo)).orElseThrow().getPaymentChanges());
    assertFalse(changes.at("/recipientChanges").isEmpty());
  }

  @Test
  @TestTransaction
  void distinctRecipientStationsAreSeparateEntities() {
    String notice = "390000000000000041";
    service.ingest(recipient(notice, "NOTICE_GENERATED", "UR3", "2024-06-15T10:00:00.000", "STAZ-A"));
    assertEquals(ReIngestionService.Outcome.APPLIED,
        service.ingest(recipient(notice, "NOTICE_GENERATED", "UR4", "2024-06-15T10:00:00.000", "STAZ-B")));

    DomainPayment p = load(notice);
    assertEquals(2, p.getRecipients().size());
    assertEquals(java.util.Set.of("STAZ-A", "STAZ-B"),
        p.getRecipients().stream().map(DomainRecipient::getStazioneRecipient).collect(java.util.stream.Collectors.toSet()));
  }

  @Test
  @TestTransaction
  void appliedChangeWritesOutboxDelta() {
    String notice = "390000000000000007";
    service.ingest(payment(notice, "TKN7", "PAYING", "U7", "2024-06-15T10:00:00.000"));
    List<OutboxEntity> rows = repository.unpublishedOutbox(50).stream()
        .filter(r -> notice.equals(r.getNoticeNumber())).toList();
    assertFalse(rows.isEmpty());
    String changes = rows.get(0).getPaymentChanges();
    assertTrue(changes.contains("logicalTransitions"));
    assertTrue(changes.contains("attemptChanges"));
  }
}
