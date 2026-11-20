package it.gov.pagopa.nodetsworker.observability.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.models.PartitionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ReEventHubConsumerTest {

  @Test
  void failedCheckpointLeavesEventEligibleForRedeliveryAfterCommittedIngestion() throws Exception {
    ReEventHubConsumer consumer = new ReEventHubConsumer();
    consumer.ingestionService = mock(ReIngestionService.class);
    consumer.objectMapper = new ObjectMapper();

    EventContext context = mock(EventContext.class);
    EventData eventData = mock(EventData.class);
    PartitionContext partitionContext = mock(PartitionContext.class);
    when(context.getEventData()).thenReturn(eventData);
    when(context.getPartitionContext()).thenReturn(partitionContext);
    when(eventData.getBodyAsString()).thenReturn("""
        {"esito":"CAMBIO_STATO","status":"payment_PAYING","idDominio":"90000000001",
         "noticeNumber":"390000000000000032","paymentToken":"TKN32","uniqueId":"U32"}
        """);
    when(eventData.getSequenceNumber()).thenReturn(32L);
    when(partitionContext.getEventHubName()).thenReturn("observability-events");
    when(partitionContext.getPartitionId()).thenReturn("0");
    doThrow(new IllegalStateException("checkpoint unavailable"))
        .doNothing().when(context).updateCheckpoint();

    Method handleEvent = ReEventHubConsumer.class.getDeclaredMethod("handleEvent", EventContext.class);
    handleEvent.setAccessible(true);
    handleEvent.invoke(consumer, context);
    handleEvent.invoke(consumer, context);

    ArgumentCaptor<ReMessage> messages = ArgumentCaptor.forClass(ReMessage.class);
    verify(consumer.ingestionService, org.mockito.Mockito.times(2)).ingest(messages.capture());
    assertEquals(2, messages.getAllValues().size());
    assertEquals("observability-events", messages.getAllValues().get(0).getEventHub());
    assertEquals("0", messages.getAllValues().get(0).getPartitionId());
    assertEquals(32L, messages.getAllValues().get(0).getSequenceNumber());

    InOrder order = inOrder(consumer.ingestionService, context);
    order.verify(consumer.ingestionService).ingest(any(ReMessage.class));
    order.verify(context).updateCheckpoint();
    order.verify(consumer.ingestionService).ingest(any(ReMessage.class));
    order.verify(context).updateCheckpoint();
  }
}
