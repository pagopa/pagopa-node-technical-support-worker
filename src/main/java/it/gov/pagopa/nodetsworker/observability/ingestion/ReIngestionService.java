package it.gov.pagopa.nodetsworker.observability.ingestion;

import io.quarkus.hibernate.orm.PersistenceUnit;
import it.gov.pagopa.nodetsworker.observability.ingestion.AdmissionFilter.Admitted;
import it.gov.pagopa.nodetsworker.observability.ingestion.AdmissionFilter.Entity;
import it.gov.pagopa.nodetsworker.observability.repository.models.IngestionAnomalyEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.InboxEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * The business core of the RE consumer (spec §4.1–§4.3). Processes one Event Hubs message in a single
 * transaction on the {@code observability} persistence unit: deduplicate, admit, validate correlation
 * keys, resolve the event time, then delegate the FSM resolution and projection/outbox write to
 * {@link IngestionProjectionWriter}. Transport and checkpointing live in {@link ReEventHubConsumer};
 * this service is pure enough to be driven directly from tests without Event Hubs.
 */
@ApplicationScoped
public class ReIngestionService {

  private static final Logger LOG = Logger.getLogger(ReIngestionService.class);
  private static final Pattern PA_FISCAL_CODE = Pattern.compile("\\d{11}");
  private static final Pattern NOTICE_NUMBER = Pattern.compile("\\d{18}");

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  @Inject
  IngestionProjectionWriter writer;

  @Inject
  PrimitiveIngestionService primitives;

  @Inject
  it.gov.pagopa.nodetsworker.observability.service.ObservabilityConfig config;

  /** The processing result, mirrored onto the inbox outcome. */
  public enum Outcome {
    APPLIED,
    NOOP,
    ANOMALY,
    DROPPED
  }

  /**
   * Process one RE message end to end. Idempotent: a redelivery (same {@code uniqueId} or transport
   * key) is a no-op. Returns {@link Outcome#DROPPED} for non-admitted messages, which are not even
   * recorded in the inbox (§4.1).
   */
  @Transactional
  public Outcome ingest(ReMessage msg) {
    // 1. Admission filter — non-admitted messages leave no trace.
    Admitted admitted = AdmissionFilter.admit(msg);
    if (admitted == null) {
      // Interface REQ/RESP are not projected, but they are counted as primitive calls (§3.3.2).
      PrimitiveAdmission.Admitted primitive = PrimitiveAdmission.admit(msg);
      return primitive == null ? Outcome.DROPPED : ingestPrimitive(msg, primitive);
    }

    // 2. Deduplication (inbox): a message already processed is a no-op.
    String dedupKey = dedupKey(msg);
    if (dedupKey == null) {
      LOG.warn("Admitted RE message without any dedup key; dropping");
      return Outcome.DROPPED;
    }
    // Serialize before the inbox read. A concurrent redelivery that waited on the metadata lock
    // must observe the first transaction's inbox row before it applies or records the event.
    writer.lockProjectionMeta();
    if (em.find(InboxEntity.class, dedupKey) != null) {
      return Outcome.NOOP;
    }

    Instant now = EventTime.nowUtc();
    Outcome outcome;

    // 3. Correlation keys: an uncorrelatable message updates nothing but is recorded as an anomaly.
    if (!validKey(msg.getIdDominio(), PA_FISCAL_CODE) || !validKey(msg.getNoticeNumber(), NOTICE_NUMBER)
        || (admitted.entity() == Entity.PAYMENT && !validToken(msg.getPaymentToken()))) {
      writer.recordAnomaly(anomaly("MISSING_KEYS", msg, "Missing or invalid correlation key", now));
      outcome = Outcome.ANOMALY;
    } else if (!PaymentFsm.isKnownState(admitted.entity(), admitted.state())) {
      writer.recordAnomaly(anomaly("UNKNOWN_STATE", msg,
          "Unsupported " + admitted.entity().name().toLowerCase() + " state: " + admitted.state(), now));
      outcome = Outcome.ANOMALY;
    } else {
      // 4. Event time (Europe/Rome -> UTC).
      EventTime.Resolved time = EventTime.resolve(msg.getDataOraEvento());
      if (time.nonExistentLocalTime()) {
        writer.recordAnomaly(anomaly("NONEXISTENT_LOCAL_TIME", msg,
            "Local time in the spring-forward gap: " + msg.getDataOraEvento(), now));
      }
      // 5-7. FSM resolution + projection/outbox write (single transaction).
      IngestionProjectionWriter.ApplyResult result =
          writer.apply(admitted, msg, time, now, config.retentionDays());
      if (result.anomaly()) {
        writer.recordAnomaly(anomaly("VALUE_CONFLICT_OR_FSM", msg,
            "Conflicting dimension value or ambiguous FSM path for " + msg.getStatus(), now));
      }
      outcome = result.anomaly() ? Outcome.ANOMALY : (result.changed() ? Outcome.APPLIED : Outcome.NOOP);
    }

    // 8. Record the inbox entry (dedup + retention of the technical record).
    em.persist(InboxEntity.builder()
        .uniqueId(dedupKey)
        .eventHub(msg.getEventHub())
        .partitionId(msg.getPartitionId())
        .sequenceNumber(msg.getSequenceNumber())
        .outcome(outcome.name())
        .processedAt(now)
        .expiresAt(now.plus(config.retentionDays(), ChronoUnit.DAYS))
        .build());
    return outcome;
  }

  /** Count one REQ/RESP: same inbox deduplication as projected events, no payment projection. */
  private Outcome ingestPrimitive(ReMessage msg, PrimitiveAdmission.Admitted primitive) {
    String dedupKey = dedupKey(msg);
    if (dedupKey == null) {
      LOG.warn("Admitted RE interface message without any dedup key; dropping");
      return Outcome.DROPPED;
    }
    writer.lockProjectionMeta();
    if (em.find(InboxEntity.class, dedupKey) != null) {
      return Outcome.NOOP;
    }
    Instant now = EventTime.nowUtc();
    EventTime.Resolved time = EventTime.resolve(msg.getDataOraEvento());
    Outcome outcome;
    if (time.instant() == null) {
      writer.recordAnomaly(anomaly("MISSING_EVENT_TIME", msg, "Primitive event without a usable time", now));
      outcome = Outcome.ANOMALY;
    } else {
      outcome = primitives.apply(primitive, msg, time.instant(), now) ? Outcome.APPLIED : Outcome.NOOP;
    }
    em.persist(InboxEntity.builder()
        .uniqueId(dedupKey)
        .eventHub(msg.getEventHub())
        .partitionId(msg.getPartitionId())
        .sequenceNumber(msg.getSequenceNumber())
        .outcome(outcome.name())
        .processedAt(now)
        .expiresAt(now.plus(config.retentionDays(), ChronoUnit.DAYS))
        .build());
    return outcome;
  }

  /** {@code uniqueId} when present, else the transport key {@code eventHub|partitionId|sequenceNumber}. */
  private static String dedupKey(ReMessage msg) {
    if (!isBlank(msg.getUniqueId()) && withinLimit(msg.getUniqueId(), 128)) {
      return msg.getUniqueId();
    }
    if (msg.getEventHub() != null && msg.getPartitionId() != null && msg.getSequenceNumber() != null) {
      String transportKey = msg.getEventHub() + "\u0000" + msg.getPartitionId() + "\u0000" + msg.getSequenceNumber();
      try {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(transportKey.getBytes(StandardCharsets.UTF_8));
        return "transport:" + HexFormat.of().formatHex(digest);
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 unavailable", e);
      }
    }
    return null;
  }

  private static IngestionAnomalyEntity anomaly(String type, ReMessage msg, String detail, Instant now) {
    return IngestionAnomalyEntity.builder()
        .type(type)
        .uniqueId(withinLimit(msg.getUniqueId(), 128) ? msg.getUniqueId() : null)
        .paFiscalCode(validKey(msg.getIdDominio(), PA_FISCAL_CODE) ? msg.getIdDominio() : null)
        .noticeNumber(validKey(msg.getNoticeNumber(), NOTICE_NUMBER) ? msg.getNoticeNumber() : null)
        .paymentToken(null)
        .detail(withinLimit(detail, 512) ? detail : detail.substring(0, 512))
        .recordedAt(now)
        .build();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static boolean validKey(String value, Pattern pattern) {
    return value != null && pattern.matcher(value).matches();
  }

  private static boolean validToken(String value) {
    return !isBlank(value) && value.length() <= 128;
  }

  private static boolean withinLimit(String value, int maxLength) {
    return value == null || value.length() <= maxLength;
  }
}
