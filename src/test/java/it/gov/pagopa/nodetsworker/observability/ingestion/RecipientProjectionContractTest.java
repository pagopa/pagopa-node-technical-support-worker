package it.gov.pagopa.nodetsworker.observability.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.repository.models.PaymentEntity;
import it.gov.pagopa.nodetsworker.observability.service.Caller;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RecipientProjectionContractTest {
  private static final String PA = "90000000009";
  private static final String NOTICE = "990000000000000001";
  @Inject ReIngestionService ingestion;
  @Inject ProjectionRepository repository;
  @Inject ObservabilityService service;
  @Inject ObjectMapper mapper;
  @Inject @PersistenceUnit("observability") EntityManager em;

  private void event(String status, String id, String date, String trigger) {
    ReMessage message = new ReMessage();
    message.setEsito("CAMBIO_STATO");
    message.setStatus(status);
    message.setIdDominio(PA);
    message.setNoticeNumber(NOTICE);
    message.setPaymentToken("contract-token");
    message.setUniqueId(id);
    message.setDataOraEvento(date == null ? null : date + ".000");
    message.setTipoEvento(trigger);
    message.setInfo("stazione_recipient=station-contract");
    var outcome = ingestion.ingest(message);
    assertTrue(outcome == ReIngestionService.Outcome.APPLIED || outcome == ReIngestionService.Outcome.ANOMALY);
  }

  private PaymentEntity payment() {
    return em.createQuery("SELECT p FROM PaymentEntity p WHERE p.paFiscalCode = :pa", PaymentEntity.class)
        .setParameter("pa", PA).getSingleResult();
  }

  @Test
  @TestTransaction
  void observedSendAfterArchiveIsRecordedWithoutInventingAnFsmEdge() throws Exception {
    event("position_NOTICE_STORED", "stored-position", "2026-10-20T10:00:00", "archiveOutcomeWithRpt");
    event("payment_NOTICE_STORED", "stored-attempt", "2026-10-20T10:00:00", "archiveOutcomeWithRpt");
    event("payment_NOTICE_SENT", "sent-attempt", "2026-10-20T09:59:00", "actualSendTrigger");
    event("payment_NOTIFIED", "notified-attempt", null, "actualNotifyTrigger");
    event("receipt_recipient_NOTIFIED", "notified-recipient", null, "recipientNotify");

    em.flush();
    em.clear();
    var detail = service.paymentDetail(Caller.implicit(), PA, NOTICE);
    assertEquals("NOTICE_STORED", detail.getState());
    assertEquals(List.of("NOTICE_STORED", "NOTICE_SENT", "NOTIFIED"),
        detail.getAttempts().get(0).getTimeline().stream().map(t -> t.getState()).toList());
    assertEquals("actualSendTrigger", detail.getAttempts().get(0).getTimeline().get(1).getTrigger());
    assertNull(detail.getAttempts().get(0).getTimeline().get(2).getTransitionAt());
    assertEquals("NOTIFIED", detail.getRecipients().get(0).getState());
    assertFalse(PaymentFsm.isAllowed(AdmissionFilter.Entity.PAYMENT, "NOTICE_STORED", "NOTICE_SENT"));
    var delta = mapper.readTree(repository.unpublishedOutbox(1000).stream()
        .filter(r -> NOTICE.equals(r.getNoticeNumber())).reduce((a, b) -> b).orElseThrow().getPaymentChanges());
    assertTrue(delta.get("logicalTransitions").isEmpty());
    assertTrue(delta.get("attemptChanges").isEmpty());
    assertEquals("recipientNotify", delta.at("/recipientChanges/0/upsert/timeline/2/trigger").asText());
    assertEquals(detail.getProjectionRevision(), delta.at("/recipientChanges/0/upsert/projectionRevision").asLong());
  }

  @Test
  @TestTransaction
  void recipientOnlyProjectionAppearsInOverviewAndCreditorDetail() {
    event("receipt_recipient_NOTICE_GENERATED", "only-recipient", "2026-10-20T10:00:00", "generate");
    // The recipient event is dated 2026-10-20 (local time): the interval must contain it to be counted.
    var window = Map.of("from", "2026-10-20T00:00:00.000Z", "to", "2026-10-21T00:00:00.000Z");
    var query = new java.util.HashMap<>(window);
    query.put("paFiscalCode", PA);
    assertEquals(1, service.overview(Caller.implicit(), query).getRecipientStates().stream()
        .filter(s -> s.getState().equals("NOTICE_GENERATED")).findFirst().orElseThrow().getCount());
    assertEquals(1, service.creditorDetail(Caller.implicit(), PA, window).getMatrices().getRecipient().size());
    var other = new java.util.HashMap<>(query);
    other.put("psp", "other");
    assertEquals(0, service.overview(Caller.implicit(), other).getCounts().getPayments());
  }

  @Test
  @TestTransaction
  void retryVisitsPreserveServerSequenceAndProvenanceWithoutDateSorting() {
    event("receipt_recipient_NOTICE_GENERATED", "retry-1", "2026-10-20T10:00:00", "generate");
    event("receipt_recipient_NOTICE_SENT", "retry-2", "2026-10-20T10:02:00", "send1");
    event("receipt_recipient_NOTICE_PENDING", "retry-3", null, "pending");
    event("receipt_recipient_NOTICE_SENT", "retry-4", "2026-10-20T10:01:00", "send2");
    em.flush();
    em.clear();
    var timeline = service.paymentDetail(Caller.implicit(), PA, NOTICE).getRecipients().get(0).getTimeline();
    assertEquals(List.of("NOTICE_GENERATED", "NOTICE_SENT", "NOTICE_PENDING", "NOTICE_SENT"),
        timeline.stream().map(t -> t.getState()).toList());
    assertEquals(List.of("generate", "send1", "pending", "send2"),
        timeline.stream().map(t -> t.getTrigger()).toList());
    assertNull(timeline.get(2).getTransitionAt());
    assertEquals("ANOMALY", timeline.get(2).getTimestampBasis().name());
  }

  @Test
  @TestTransaction
  void independentRecipientExpiryEmitsRemovalWhileAttemptAndParentRemain() throws Exception {
    event("payment_PAYING", "retained-attempt", "2026-10-20T10:00:00", "activate");
    event("receipt_recipient_NOTICE_GENERATED", "expired-recipient", "2026-01-01T10:00:00", "generate");
    Instant now = Instant.parse("2026-10-23T10:00:00Z");
    long beforeRecipientExpiry = payment().getProjectionRevision();
    repository.sweepExpired(now, now.minusSeconds(60));
    assertTrue(payment().getRecipients().isEmpty());
    assertEquals(1, payment().getAttempts().size());
    var recipientRemoval = repository.unpublishedOutbox(1000).stream()
        .filter(r -> NOTICE.equals(r.getNoticeNumber())).reduce((a, b) -> b).orElseThrow();
    var recipientChanges = mapper.readTree(recipientRemoval.getPaymentChanges());
    assertEquals(beforeRecipientExpiry + 1, recipientRemoval.getPaymentRevision());
    assertEquals(1, recipientChanges.get("removedRecipientIds").size());
    assertTrue(recipientChanges.get("removedAttemptIds").isEmpty());
    assertFalse(recipientChanges.get("deleted").asBoolean());
    payment().getAttempts().get(0).setExpiresAt(now.minusSeconds(1));
    long revision = payment().getProjectionRevision();
    repository.sweepExpired(now, now.minusSeconds(60));
    em.flush();
    assertFalse(repository.existsPayment(PA, NOTICE));
    var removal = repository.unpublishedOutbox(1000).stream().filter(r -> NOTICE.equals(r.getNoticeNumber()))
        .reduce((a, b) -> b).orElseThrow();
    assertEquals(revision + 1, removal.getPaymentRevision());
    var changes = mapper.readTree(removal.getPaymentChanges());
    assertTrue(changes.get("deleted").asBoolean());
    assertTrue(changes.get("removedRecipientIds").isEmpty());
    assertEquals(1, changes.get("removedAttemptIds").size());
  }

  @Test
  @TestTransaction
  void expiredRecipientWithoutAttemptsEmitsItsOwnRemoval() throws Exception {
    event("receipt_recipient_NOTICE_GENERATED", "recipient-to-expire", "2026-01-01T10:00:00", "generate");
    Instant now = Instant.parse("2026-10-23T10:00:00Z");
    repository.sweepExpired(now, now.minusSeconds(60));
    var changes = mapper.readTree(repository.unpublishedOutbox(1000).stream()
        .filter(r -> NOTICE.equals(r.getNoticeNumber())).reduce((a, b) -> b).orElseThrow().getPaymentChanges());
    assertTrue(changes.get("deleted").asBoolean());
    assertTrue(changes.get("removedAttemptIds").isEmpty());
    assertEquals(1, changes.get("removedRecipientIds").size());
  }

  @Test
  @TestTransaction
  void lastAttemptExpiryCapsRecipientAvailabilityWithoutExtendingItsOwnDeadline() throws Exception {
    event("payment_PAYING", "parent-to-expire", "2026-01-01T10:00:00", "activate");
    event("receipt_recipient_NOTICE_GENERATED", "recent-recipient", "2026-10-20T10:00:00", "generate");
    Instant now = Instant.parse("2026-10-23T10:00:00Z");
    repository.sweepExpired(now, now.minusSeconds(60));
    em.flush();
    assertFalse(repository.existsPayment(PA, NOTICE));
    var changes = mapper.readTree(repository.unpublishedOutbox(1000).stream()
        .filter(r -> NOTICE.equals(r.getNoticeNumber())).reduce((a, b) -> b).orElseThrow().getPaymentChanges());
    assertTrue(changes.get("deleted").asBoolean());
    assertEquals(1, changes.get("removedAttemptIds").size());
    assertEquals(1, changes.get("removedRecipientIds").size());
  }

  @Test
  @TestTransaction
  void earlierKnownRecipientTimestampCanShortenButNeverExtendItsRetention() {
    event("receipt_recipient_NOTICE_GENERATED", "deadline-1", "2026-10-20T10:00:00", "generate");
    event("receipt_recipient_NOTICE_SENT", "deadline-2", "2026-10-19T10:00:00", "send");
    Instant deadline = Instant.parse("2026-10-19T08:00:00Z").plusSeconds(60L * 86400);
    assertEquals(deadline, payment().getRecipients().get(0).getExpiresAt());
    event("receipt_recipient_NOTICE_PENDING", "deadline-3", "2026-10-21T10:00:00", "pending");
    assertEquals(deadline, payment().getRecipients().get(0).getExpiresAt());
  }

  @Test
  @TestTransaction
  void lateActualGenerationCorrectsInferredOccurrenceWithoutRegressingRecipient() throws Exception {
    event("receipt_recipient_NOTICE_SENT", "late-generation-send", "2026-10-20T10:00:00", "actualSend");
    long revision = payment().getProjectionRevision();
    long aggregateRevision = repository.aggregateRevision();
    long outboxCount = repository.unpublishedOutbox(1000).size();
    event("receipt_recipient_NOTICE_GENERATED", "late-generation-source", "2026-09-01T10:00:00",
        "actualGenerate");
    em.flush();
    em.clear();

    var entity = payment().getRecipients().get(0);
    assertEquals("NOTICE_SENT", entity.getCurrentState());
    assertEquals(2, entity.getTimeline().size());
    assertEquals(Instant.parse("2026-09-01T08:00:00Z"), entity.getTimeline().get(0).getTransitionAt());
    assertEquals("SOURCE_EVENT", entity.getTimeline().get(0).getTimestampBasis().name());
    assertEquals("actualGenerate", entity.getTimeline().get(0).getTrigger());
    assertEquals("actualSend", entity.getTimeline().get(1).getTrigger());
    assertEquals(Instant.parse("2026-10-31T08:00:00Z"), entity.getExpiresAt());
    assertEquals(revision + 1, payment().getProjectionRevision());
    assertEquals(aggregateRevision + 1, repository.aggregateRevision());
    assertEquals(outboxCount + 1, repository.unpublishedOutbox(1000).size());
    var row = repository.unpublishedOutbox(1000).stream().filter(r -> NOTICE.equals(r.getNoticeNumber()))
        .reduce((a, b) -> b).orElseThrow();
    var changes = mapper.readTree(row.getPaymentChanges());
    assertTrue(changes.get("logicalTransitions").isEmpty());
    assertTrue(changes.get("attemptChanges").isEmpty());
    assertTrue(changes.at("/recipientChanges/0/transitions").isEmpty());
    assertEquals(revision + 1, changes.at("/recipientChanges/0/upsert/projectionRevision").asLong());
    assertEquals("NOTICE_SENT", changes.at("/recipientChanges/0/upsert/state").asText());
    assertEquals("actualGenerate", changes.at("/recipientChanges/0/upsert/timeline/0/trigger").asText());
    assertEquals("2026-09-01T08:00:00.000Z",
        changes.at("/recipientChanges/0/upsert/timeline/0/transitionAt").asText());

    ReMessage duplicateSource = new ReMessage();
    duplicateSource.setEsito("CAMBIO_STATO");
    duplicateSource.setStatus("receipt_recipient_NOTICE_GENERATED");
    duplicateSource.setIdDominio(PA);
    duplicateSource.setNoticeNumber(NOTICE);
    duplicateSource.setInfo("stazione_recipient=station-contract");
    duplicateSource.setUniqueId("late-generation-unrelated-regression");
    duplicateSource.setDataOraEvento("2026-09-02T10:00:00.000");
    duplicateSource.setTipoEvento("mustNotOverwriteObservedGeneration");
    assertEquals(ReIngestionService.Outcome.NOOP, ingestion.ingest(duplicateSource));
    assertEquals(revision + 1, payment().getProjectionRevision());
    assertEquals("actualGenerate", payment().getRecipients().get(0).getTimeline().get(0).getTrigger());
  }

  @Test
  @TestTransaction
  void lateSourceDoesNotGuessWhichRepeatedRecipientOccurrenceItBelongsTo() {
    event("receipt_recipient_NOTICE_PENDING", "ambiguous-pending", "2026-10-20T10:00:00", "pending");
    event("receipt_recipient_NOTICE_SENT", "ambiguous-retry", "2026-10-20T10:01:00", "retry");
    event("receipt_recipient_NOTIFIED", "ambiguous-notified", "2026-10-20T10:02:00", "notify");
    long revision = payment().getProjectionRevision();
    ReMessage ambiguous = new ReMessage();
    ambiguous.setEsito("CAMBIO_STATO");
    ambiguous.setStatus("receipt_recipient_NOTICE_SENT");
    ambiguous.setIdDominio(PA);
    ambiguous.setNoticeNumber(NOTICE);
    ambiguous.setInfo("stazione_recipient=station-contract");
    ambiguous.setUniqueId("ambiguous-late-send");
    ambiguous.setDataOraEvento("2026-09-01T10:00:00.000");
    ambiguous.setTipoEvento("unknownSendOccurrence");
    assertEquals(ReIngestionService.Outcome.NOOP, ingestion.ingest(ambiguous));
    assertEquals(revision, payment().getProjectionRevision());
    assertEquals("NOTIFIED", payment().getRecipients().get(0).getCurrentState());
  }
}
