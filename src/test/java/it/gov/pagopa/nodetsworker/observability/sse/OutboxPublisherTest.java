package it.gov.pagopa.nodetsworker.observability.sse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.hibernate.orm.PersistenceUnit;
import it.gov.pagopa.nodetsworker.observability.domain.DomainAttempt;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.ingestion.ReIngestionService;
import it.gov.pagopa.nodetsworker.observability.ingestion.ReMessage;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.repository.models.OutboxEntity;
import it.gov.pagopa.nodetsworker.observability.service.FreshnessService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

@QuarkusTest
class OutboxPublisherTest {
  @Inject ReIngestionService ingestion;
  @Inject ProjectionRepository repository;
  @Inject ObjectMapper mapper;

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  private static OutboxPublisher mockedPublisher() {
    OutboxPublisher publisher = new OutboxPublisher();
    publisher.repository = mock(ProjectionRepository.class);
    publisher.hub = mock(SseHub.class);
    publisher.freshnessService = mock(FreshnessService.class);
    publisher.objectMapper = new ObjectMapper();
    return publisher;
  }

  private static DomainPayment payment(String notice, long revision, String token) {
    return DomainPayment.builder().paFiscalCode("90000000001").noticeNumber(notice).projectionRevision(revision)
        .attempts(new ArrayList<>(List.of(DomainAttempt.builder().attemptId(notice + "-a")
            .paymentToken(token).build())))
        .build();
  }

  private static OutboxEntity paymentRow(long id, long sequence, String notice) {
    return OutboxEntity.builder().id(id).sequenceNo(sequence).aggregateRevision(sequence)
        .paFiscalCode("90000000001").noticeNumber(notice).paymentChanges("{}").createdAt(Instant.now()).build();
  }

  @Test
  void idleTickDoesNotReloadTheProjection() {
    OutboxPublisher publisher = mockedPublisher();
    when(publisher.hub.lastSnapshot()).thenReturn(List.of(payment("1", 1, null)));
    when(publisher.repository.unpublishedOutbox(anyInt())).thenReturn(List.of());

    publisher.poll();

    verify(publisher.repository).unpublishedOutbox(OutboxPublisher.DEFAULT_BATCH_SIZE);
    verify(publisher.repository, never()).loadVisible(any());
    verify(publisher.repository, never()).loadByKeys(any());
    verify(publisher.hub, never()).publish(any());
  }

  @Test
  void firstTickInitializesTheSnapshotWithOneFullLoad() {
    OutboxPublisher publisher = mockedPublisher();
    List<DomainPayment> all = List.of(payment("1", 1, "secret"));
    when(publisher.hub.lastSnapshot()).thenReturn(null);
    when(publisher.repository.unpublishedOutbox(anyInt())).thenReturn(List.of());
    when(publisher.repository.loadVisible(null)).thenReturn(all);

    publisher.poll();

    verify(publisher.repository, times(1)).loadVisible(null);
    verify(publisher.hub).setLastSnapshot(all);
    assertNull(all.get(0).getAttempts().get(0).getPaymentToken(), "snapshots never keep raw tokens");
  }

  @Test
  void batchReloadsOnlyTheChangedPaymentsAndMergesThemIntoTheSnapshot() {
    OutboxPublisher publisher = mockedPublisher();
    publisher.batchSize = 7;
    DomainPayment a = payment("A", 1, null);
    DomainPayment b = payment("B", 1, null);
    DomainPayment c = payment("C", 1, null);
    List<DomainPayment> before = List.of(a, b, c);
    when(publisher.hub.lastSnapshot()).thenReturn(before);
    when(publisher.repository.unpublishedOutbox(7)).thenReturn(List.of(
        paymentRow(1, 10, "B"), paymentRow(2, 11, "C"), paymentRow(3, 12, "B"), paymentRow(4, 13, "D"),
        OutboxEntity.builder().id(5L).sequenceNo(14).aggregateRevision(14).primitiveChanges("[]")
            .createdAt(Instant.now()).build()));
    DomainPayment b2 = payment("B", 2, "raw-token");
    DomainPayment d = payment("D", 1, "raw-token");
    when(publisher.repository.loadByKeys(any())).thenReturn(List.of(d, b2));

    publisher.poll();

    verify(publisher.repository, never()).loadVisible(any());
    verify(publisher.repository).loadByKeys(List.of(
        new ProjectionRepository.PaymentKey("90000000001", "B"),
        new ProjectionRepository.PaymentKey("90000000001", "C"),
        new ProjectionRepository.PaymentKey("90000000001", "D")));
    ArgumentCaptor<HistoryEntry> delivered = ArgumentCaptor.forClass(HistoryEntry.class);
    verify(publisher.hub).publish(delivered.capture());
    assertSame(before, delivered.getValue().before());
    assertEquals(List.of(a, b, c), before, "the previous snapshot is never mutated");
    assertEquals(List.of(a, b2, d), delivered.getValue().after(), "updated in place, deleted dropped, new appended");
    assertNull(b2.getAttempts().get(0).getPaymentToken());
    assertNull(d.getAttempts().get(0).getPaymentToken());
    assertEquals(14, delivered.getValue().sequence());
    verify(publisher.hub).setLastSnapshot(delivered.getValue().after());
    verify(publisher.repository, times(1)).markPublished(eq(List.of(1L, 2L, 3L, 4L, 5L)), any());
  }

  @Test
  @TestTransaction
  void drainsMoreRowsThanTheBatchInSequenceWithoutLossOrDuplication() {
    String pa = "91000000099";
    int batch = 20;
    int total = batch * 2 + 7;
    long base = 9_000_000L;
    for (int i = 0; i < total; i++) {
      em.persist(OutboxEntity.builder().sequenceNo(base + i).aggregateRevision(1).paFiscalCode(pa)
          .noticeNumber(String.format("%018d", i)).paymentChanges("{}").createdAt(Instant.now()).build());
    }
    em.flush();
    OutboxPublisher publisher = new OutboxPublisher();
    publisher.repository = repository;
    publisher.objectMapper = mapper;
    publisher.freshnessService = mock(FreshnessService.class);
    publisher.hub = mock(SseHub.class);
    publisher.batchSize = batch;
    when(publisher.hub.lastSnapshot()).thenReturn(List.of());

    List<String> delivered = new ArrayList<>();
    for (int poll = 0; poll < 10 && delivered.size() < total; poll++) {
      clearInvocations(publisher.hub);
      publisher.poll();
      ArgumentCaptor<HistoryEntry> entry = ArgumentCaptor.forClass(HistoryEntry.class);
      verify(publisher.hub).publish(entry.capture());
      List<String> mine = entry.getValue().paymentChanges().stream()
          .filter(change -> pa.equals(change.paFiscalCode())).map(HistoryEntry.PaymentChange::noticeNumber).toList();
      assertTrue(entry.getValue().paymentChanges().size() <= batch, "a drain never exceeds the batch");
      delivered.addAll(mine);
    }

    Set<String> unique = new HashSet<>(delivered);
    assertEquals(total, delivered.size(), "every row delivered exactly once");
    assertEquals(total, unique.size());
    assertEquals(delivered.stream().sorted().toList(), delivered, "delivered in sequence order");
    assertEquals(0L, em.createQuery("SELECT COUNT(o) FROM OutboxEntity o WHERE o.paFiscalCode = :pa "
        + "AND o.published = false", Long.class).setParameter("pa", pa).getSingleResult());
  }

  @Test
  void recipientRemovalIsPublishedThenInvalidatesReplayThatCouldReintroduceExpiredData() {
    OutboxPublisher publisher = new OutboxPublisher();
    publisher.repository = mock(ProjectionRepository.class);
    publisher.hub = mock(SseHub.class);
    publisher.freshnessService = mock(FreshnessService.class);
    publisher.objectMapper = new ObjectMapper();
    when(publisher.repository.loadVisible(null)).thenReturn(List.of());
    when(publisher.repository.unpublishedOutbox(anyInt())).thenReturn(List.of(
        OutboxEntity.builder().id(1L).sequenceNo(10).aggregateRevision(7).paymentRevision(5L)
            .paFiscalCode("90000000009").noticeNumber("990000000000000001")
            .paymentChanges("{\"recipientChanges\":[],\"removedRecipientIds\":[\"recipient-1\"],\"deleted\":false}")
            .createdAt(Instant.now()).build()));

    publisher.poll();

    InOrder delivery = inOrder(publisher.hub, publisher.repository);
    delivery.verify(publisher.hub).publish(argThat(entry ->
        entry.revision() == 7 && entry.paymentChanges().get(0).projectionRevision() == 5));
    delivery.verify(publisher.hub).expireReplay();
    delivery.verify(publisher.hub).setLastSnapshot(List.of());
    delivery.verify(publisher.repository).markPublished(eq(List.of(1L)), any());
  }

  @Test
  @TestTransaction
  void lateRecipientSourceCorrectionIsPublishedAsAnAuthoritativeUpsert() {
    String pa = "90000000007";
    String notice = "990000000000000007";
    ReMessage event = new ReMessage();
    event.setEsito("CAMBIO_STATO");
    event.setStatus("receipt_recipient_NOTICE_SENT");
    event.setIdDominio(pa);
    event.setNoticeNumber(notice);
    event.setInfo("stazione_recipient=publication-station");
    event.setUniqueId("publication-send");
    event.setDataOraEvento("2026-10-20T10:00:00.000");
    event.setTipoEvento("actualSend");
    assertEquals(ReIngestionService.Outcome.APPLIED, ingestion.ingest(event));
    var before = repository.loadVisible(null);
    long beforeRevision = repository.aggregateRevision();
    event.setStatus("receipt_recipient_NOTICE_GENERATED");
    event.setUniqueId("publication-generation");
    event.setDataOraEvento("2026-09-01T10:00:00.000");
    event.setTipoEvento("actualGenerate");
    ingestion.ingest(event);

    OutboxPublisher publisher = new OutboxPublisher();
    publisher.repository = repository;
    publisher.objectMapper = mapper;
    publisher.freshnessService = mock(FreshnessService.class);
    publisher.hub = mock(SseHub.class);
    when(publisher.hub.lastSnapshot()).thenReturn(before);
    publisher.poll();

    ArgumentCaptor<HistoryEntry> delivered = ArgumentCaptor.forClass(HistoryEntry.class);
    verify(publisher.hub).publish(delivered.capture());
    var correction = delivered.getValue().paymentChanges().stream()
        .filter(change -> notice.equals(change.noticeNumber())).reduce((a, b) -> b).orElseThrow();
    var changes = mapper.valueToTree(correction.changes());
    assertEquals(beforeRevision + 1, delivered.getValue().revision());
    assertEquals("actualGenerate", changes.at("/recipientChanges/0/upsert/timeline/0/trigger").asText());
    assertEquals("SOURCE_EVENT", changes.at("/recipientChanges/0/upsert/timeline/0/timestampBasis").asText());
    assertEquals("NOTICE_SENT", changes.at("/recipientChanges/0/upsert/state").asText());
    assertTrue(changes.at("/recipientChanges/0/transitions").isEmpty());
  }
}
