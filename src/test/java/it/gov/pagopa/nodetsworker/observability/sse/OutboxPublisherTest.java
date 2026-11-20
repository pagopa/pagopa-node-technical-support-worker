package it.gov.pagopa.nodetsworker.observability.sse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.observability.ingestion.ReIngestionService;
import it.gov.pagopa.nodetsworker.observability.ingestion.ReMessage;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.repository.models.OutboxEntity;
import it.gov.pagopa.nodetsworker.observability.service.FreshnessService;
import java.time.Instant;
import java.util.List;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

@QuarkusTest
class OutboxPublisherTest {
  @Inject ReIngestionService ingestion;
  @Inject ProjectionRepository repository;
  @Inject ObjectMapper mapper;

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
    delivery.verify(publisher.repository).markPublished(eq(1L), any());
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
