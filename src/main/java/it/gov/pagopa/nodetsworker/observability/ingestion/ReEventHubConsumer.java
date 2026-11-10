package it.gov.pagopa.nodetsworker.observability.ingestion;

import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Event Hubs transport for the Registro Eventi (RE) consumer (spec §4.1). Owns only the SDK wiring and
 * per-partition checkpointing; all business logic lives in {@link ReIngestionService}. For each event
 * the body is parsed into a {@link ReMessage}, the transport keys are filled from the
 * {@link EventContext}, the message is ingested in its own transaction and — only after a successful
 * commit — the partition checkpoint is advanced. On a crash between commit and checkpoint the event is
 * redelivered and the inbox dedup makes the reprocessing a no-op.
 *
 * <p>Started only when {@code observability.ingestion.enabled=true} (off in dev/test), so the rest of
 * the worker and the read-side run without an Event Hubs connection.
 */
@ApplicationScoped
public class ReEventHubConsumer {

  private static final Logger LOG = Logger.getLogger(ReEventHubConsumer.class);

  @ConfigProperty(name = "observability.ingestion.enabled", defaultValue = "false")
  boolean enabled;

  @ConfigProperty(name = "observability.ingestion.connection-string")
  Optional<String> connectionString;

  @ConfigProperty(name = "observability.ingestion.event-hub-name")
  Optional<String> eventHubName;

  @ConfigProperty(name = "observability.ingestion.consumer-group", defaultValue = "$Default")
  String consumerGroup;

  @ConfigProperty(name = "observability.ingestion.checkpoint.storage-connection-string")
  Optional<String> checkpointStorageConnectionString;

  @ConfigProperty(name = "observability.ingestion.checkpoint.container", defaultValue = "observability-checkpoints")
  String checkpointContainer;

  @Inject
  ReIngestionService ingestionService;

  @Inject
  ObjectMapper objectMapper;

  private volatile EventProcessorClient processor;

  void onStart(@Observes StartupEvent event) {
    if (!enabled) {
      LOG.info("Observability RE ingestion disabled (observability.ingestion.enabled=false)");
      return;
    }
    if (connectionString.isEmpty() || eventHubName.isEmpty() || checkpointStorageConnectionString.isEmpty()) {
      LOG.error("Observability RE ingestion enabled but connection-string / event-hub-name / "
          + "checkpoint.storage-connection-string are not configured; consumer not started");
      return;
    }
    BlobContainerAsyncClient blobClient = new BlobContainerClientBuilder()
        .connectionString(checkpointStorageConnectionString.get())
        .containerName(checkpointContainer)
        .buildAsyncClient();
    processor = new EventProcessorClientBuilder()
        .connectionString(connectionString.get(), eventHubName.get())
        .consumerGroup(consumerGroup)
        .checkpointStore(new BlobCheckpointStore(blobClient))
        .processEvent(this::handleEvent)
        .processError(this::handleError)
        .buildEventProcessorClient();
    processor.start();
    LOG.infof("Observability RE ingestion started (eventHub=%s, consumerGroup=%s)",
        eventHubName.get(), consumerGroup);
  }

  void onStop(@Observes ShutdownEvent event) {
    if (processor != null) {
      processor.stop();
      LOG.info("Observability RE ingestion stopped");
    }
  }

  private void handleEvent(EventContext context) {
    try {
      String body = context.getEventData().getBodyAsString();
      ReMessage message = objectMapper.readValue(body, ReMessage.class);
      message.setEventHub(context.getPartitionContext().getEventHubName());
      message.setPartitionId(context.getPartitionContext().getPartitionId());
      message.setSequenceNumber(context.getEventData().getSequenceNumber());

      ingestionService.ingest(message);
      // Checkpoint only after the ingestion transaction has committed (spec §4.1/§4.2).
      context.updateCheckpoint();
    } catch (RuntimeException e) {
      // Persistent errors must stay observable and not be turned into a successful update (§4.1):
      // do NOT checkpoint, so the event is redelivered.
      LOG.errorf(e, "Failed to ingest RE event (partition=%s, seq=%s); will be redelivered",
          context.getPartitionContext().getPartitionId(), context.getEventData().getSequenceNumber());
    } catch (Exception e) {
      LOG.errorf(e, "Malformed RE event (partition=%s, seq=%s)",
          context.getPartitionContext().getPartitionId(), context.getEventData().getSequenceNumber());
      // A body we cannot even parse is not retryable: checkpoint to avoid a poison-message loop.
      context.updateCheckpoint();
    }
  }

  private void handleError(ErrorContext context) {
    LOG.errorf(context.getThrowable(), "Event Hubs processing error on partition %s",
        context.getPartitionContext().getPartitionId());
  }
}
