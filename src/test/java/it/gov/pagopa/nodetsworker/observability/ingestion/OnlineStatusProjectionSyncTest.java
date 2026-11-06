package it.gov.pagopa.nodetsworker.observability.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.exception.OnlineSourceStateException;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.service.OnlineStatusSyncService;
import it.gov.pagopa.nodetsworker.observability.repository.models.AttemptEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.OutboxEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PaymentEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.ProjectionMetaEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlineStatusProjectionSyncTest {

  private static final String PA = "90000000001";
  private static final long RETENTION_DAYS = 30;

  @Inject
  IngestionProjectionWriter writer;

  @Inject
  OnlineStatusSyncService syncService;

  @Inject
  ProjectionRepository repository;

  @Inject
  ObjectMapper objectMapper;

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  @Inject
  @io.quarkus.agroal.DataSource("online-ndp001")
  DataSource onlineDataSource;

  @BeforeEach
  void createOnlineTables() throws SQLException {
    try (Connection connection = onlineDataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA IF NOT EXISTS online");
      statement.execute("DROP TABLE IF EXISTS online.position_status_snapshot");
      statement.execute("DROP TABLE IF EXISTS online.position_payment_status_snapshot");
      statement.execute("""
          CREATE TABLE online.position_status_snapshot (
            id BIGINT PRIMARY KEY,
            pa_fiscal_code VARCHAR(16) NOT NULL,
            notice_id VARCHAR(32) NOT NULL,
            status VARCHAR(64) NOT NULL,
            inserted_timestamp TIMESTAMP NOT NULL,
            updated_timestamp TIMESTAMP
          )
          """);
      statement.execute("""
          CREATE TABLE online.position_payment_status_snapshot (
            id BIGINT PRIMARY KEY,
            pa_fiscal_code VARCHAR(16) NOT NULL,
            notice_id VARCHAR(32) NOT NULL,
            payment_token VARCHAR(128) NOT NULL,
            status VARCHAR(64) NOT NULL,
            inserted_timestamp TIMESTAMP NOT NULL,
            updated_timestamp TIMESTAMP
          )
          """);
    }
  }

  @Test
  @TestTransaction
  void logicalSourceStateOverridesRejectedRegressionWithOneSourceTransition() {
    String notice = "790000000000000001";
    Instant originalAt = Instant.parse("2024-06-15T08:00:00Z");
    Instant sourceAt = Instant.parse("2024-06-15T08:01:00Z");
    assertTrue(writer.reconcileLogical(PA, notice, "NOTIFIED", originalAt));

    long paymentRevision = payment(notice).getProjectionRevision();
    long aggregateRevision = meta().getAggregateRevision();
    long sequence = meta().getLastSequence();
    long outboxCount = outboxCount(notice);

    assertTrue(writer.reconcileLogical(PA, notice, "PAYING", sourceAt));

    PaymentEntity payment = payment(notice);
    assertEquals("PAYING", payment.getCurrentState());
    assertEquals(2, payment.getTimeline().size());
    assertEquals("PAYING", payment.getTimeline().get(1).getState());
    assertEquals(sourceAt, payment.getTimeline().get(1).getTransitionAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, payment.getTimeline().get(1).getTimestampBasis());
    assertEquals(paymentRevision + 1, payment.getProjectionRevision());
    assertEquals(aggregateRevision + 1, meta().getAggregateRevision());
    assertEquals(sequence + 1, meta().getLastSequence());
    assertEquals(outboxCount + 1, outboxCount(notice));
  }

  @Test
  @TestTransaction
  void missingLogicalPaymentIsCreatedWithOnlyOneSourceTransition() {
    String notice = "790000000000000002";
    Instant sourceAt = Instant.parse("2024-06-15T08:02:00Z");

    assertTrue(writer.reconcileLogical(PA, notice, "PAID", sourceAt));

    PaymentEntity payment = payment(notice);
    assertEquals("PAID", payment.getCurrentState());
    assertEquals(1, payment.getTimeline().size());
    assertEquals(TimestampBasis.SOURCE_SYNC, payment.getTimeline().get(0).getTimestampBasis());
    assertEquals(sourceAt, payment.getTimeline().get(0).getTransitionAt());
    assertEquals(1, payment.getProjectionRevision());
    assertEquals(1, outboxCount(notice));
    DomainPayment projected = repository.loadVisible(PA).stream()
        .filter(candidate -> notice.equals(candidate.getNoticeNumber()))
        .findFirst().orElseThrow();
    assertEquals("PAID", projected.currentState());
    assertEquals(1, projected.getTimeline().size());
  }

  @Test
  @TestTransaction
  void attemptRegressionUpdatesOnlyNamedTokenAndAppendsNoIntermediateStates() {
    String notice = "790000000000000003";
    Instant at = Instant.parse("2024-06-15T08:03:00Z");
    assertTrue(writer.reconcileLogical(PA, notice, "PAYING", at));
    assertTrue(writer.reconcileAttempt(PA, notice, "token-a", "NOTIFIED", at,
        null, null, RETENTION_DAYS));
    assertTrue(writer.reconcileAttempt(PA, notice, "token-b", "PAYMENT_RESERVED", at,
        null, null, RETENTION_DAYS));

    PaymentEntity payment = payment(notice);
    AttemptEntity target = attempt(payment, "token-a");
    AttemptEntity other = attempt(payment, "token-b");
    int targetTimelineSize = target.getTimeline().size();
    int otherTimelineSize = other.getTimeline().size();
    String otherState = other.getCurrentState();
    long paymentRevision = payment.getProjectionRevision();
    long aggregateRevision = meta().getAggregateRevision();
    long sequence = meta().getLastSequence();
    long outboxCount = outboxCount(notice);
    Instant sourceAt = at.plusSeconds(60);

    assertTrue(writer.reconcileAttempt(PA, notice, "token-a", "PAYING", sourceAt,
        null, null, RETENTION_DAYS));

    payment = payment(notice);
    target = attempt(payment, "token-a");
    other = attempt(payment, "token-b");
    assertEquals("PAYING", target.getCurrentState());
    assertEquals(targetTimelineSize + 1, target.getTimeline().size());
    assertEquals("PAYING", target.getTimeline().get(targetTimelineSize).getState());
    assertEquals(sourceAt, target.getTimeline().get(targetTimelineSize).getTransitionAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, target.getTimeline().get(targetTimelineSize).getTimestampBasis());
    assertEquals(otherTimelineSize, other.getTimeline().size());
    assertEquals(otherState, other.getCurrentState());
    assertEquals(paymentRevision + 1, payment.getProjectionRevision());
    assertEquals(aggregateRevision + 1, meta().getAggregateRevision());
    assertEquals(sequence + 1, meta().getLastSequence());
    assertEquals(outboxCount + 1, outboxCount(notice));
  }

  @Test
  @TestTransaction
  void attemptCreationWithLogicalSnapshotUsesSeparateSourceTimestampsInOneOutboxDelta() throws Exception {
    String notice = "790000000000000004";
    Instant logicalUpdatedAt = Instant.parse("2024-06-15T08:04:00Z");
    Instant attemptUpdatedAt = Instant.parse("2024-06-15T08:05:00Z");
    long aggregateRevision = metaRevisionOrDefault();
    long sequence = metaSequenceOrDefault();

    assertTrue(writer.reconcileAttempt(PA, notice, "token-parent", "PAYMENT_RESERVED", attemptUpdatedAt,
        "PAID", logicalUpdatedAt, RETENTION_DAYS));

    PaymentEntity payment = payment(notice);
    AttemptEntity attempt = attempt(payment, "token-parent");
    assertEquals(1, payment.getTimeline().size());
    assertEquals("PAID", payment.getTimeline().get(0).getState());
    assertEquals(logicalUpdatedAt, payment.getTimeline().get(0).getTransitionAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, payment.getTimeline().get(0).getTimestampBasis());
    assertEquals(1, attempt.getTimeline().size());
    assertEquals("PAYMENT_RESERVED", attempt.getTimeline().get(0).getState());
    assertEquals(attemptUpdatedAt, attempt.getTimeline().get(0).getTransitionAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, attempt.getTimeline().get(0).getTimestampBasis());
    assertEquals(attemptUpdatedAt, attempt.getAttemptStartedAt());
    assertEquals(TimestampBasis.INFERRED, attempt.getStartTimestampBasis());
    assertEquals(1, payment.getProjectionRevision());
    assertEquals(aggregateRevision + 1, meta().getAggregateRevision());
    assertEquals(sequence + 1, meta().getLastSequence());
    assertEquals(1, outboxCount(notice));

    JsonNode changes = outbox(notice).getPaymentChanges() == null
        ? null : objectMapper.readTree(outbox(notice).getPaymentChanges());
    assertNotNull(changes);
    assertEquals("PAID", changes.at("/logicalTransitions/0/state").asText());
    assertEquals("SOURCE_SYNC", changes.at("/logicalTransitions/0/timestampBasis").asText());
    assertTrue(changes.at("/attemptChanges/0/transitions").isEmpty());
    assertEquals("PAYMENT_RESERVED", changes.at("/attemptChanges/0/upsert/timeline/0/state").asText());
    assertEquals("SOURCE_SYNC",
        changes.at("/attemptChanges/0/upsert/timeline/0/timestampBasis").asText());
  }

  @Test
  @TestTransaction
  void insertedTimestampFallbacksArePreservedAsResolvedInstantInputs() {
    String notice = "790000000000000005";
    Instant logicalInsertedAt = Instant.parse("2024-06-15T08:06:00Z");
    Instant attemptInsertedAt = Instant.parse("2024-06-15T08:07:00Z");

    assertTrue(writer.reconcileAttempt(PA, notice, "token-fallback-parent", "PAYMENT_RESERVED",
        attemptInsertedAt, "PAID", logicalInsertedAt, RETENTION_DAYS));

    PaymentEntity payment = payment(notice);
    AttemptEntity attempt = attempt(payment, "token-fallback-parent");
    assertEquals(logicalInsertedAt, payment.getTimeline().get(0).getTransitionAt());
    assertEquals(attemptInsertedAt, attempt.getTimeline().get(0).getTransitionAt());

    String inferredNotice = "790000000000000006";
    Instant inferredAt = Instant.parse("2024-06-15T08:08:00Z"); // attempt updatedTimestamp fallback
    assertTrue(writer.reconcileAttempt(PA, inferredNotice, "token-fallback-inferred",
        "PAYMENT_RESERVED", inferredAt, null, null, RETENTION_DAYS));

    PaymentEntity inferredPayment = payment(inferredNotice);
    assertTrue(inferredPayment.getTimeline().isEmpty());
    assertNull(inferredPayment.getCurrentState());
    assertTrue(inferredPayment.isTimelineIncomplete());
    AttemptEntity inferredAttempt = attempt(inferredPayment, "token-fallback-inferred");
    assertEquals(1, inferredAttempt.getTimeline().size());
    assertEquals("PAYMENT_RESERVED", inferredAttempt.getTimeline().get(0).getState());
    assertEquals(inferredAt, inferredAttempt.getTimeline().get(0).getTransitionAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, inferredAttempt.getTimeline().get(0).getTimestampBasis());
    assertEquals(TimestampBasis.INFERRED, inferredAttempt.getStartTimestampBasis());

    String insertedNotice = "790000000000000012";
    Instant insertedAt = Instant.parse("2024-06-15T08:12:00Z"); // attempt insertedTimestamp fallback
    assertTrue(writer.reconcileAttempt(PA, insertedNotice, "token-inserted-fallback",
        "PAYMENT_RESERVED", insertedAt, null, null, RETENTION_DAYS));
    PaymentEntity insertedPayment = payment(insertedNotice);
    assertTrue(insertedPayment.getTimeline().isEmpty());
    assertNull(insertedPayment.getCurrentState());
    assertTrue(insertedPayment.isTimelineIncomplete());
    AttemptEntity insertedAttempt = attempt(insertedPayment, "token-inserted-fallback");
    assertEquals(insertedAt, insertedAttempt.getTimeline().get(0).getTransitionAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, insertedAttempt.getTimeline().get(0).getTimestampBasis());

    String payingNotice = "790000000000000011";
    Instant payingAt = Instant.parse("2024-06-15T08:13:00Z");
    assertTrue(writer.reconcileAttempt(PA, payingNotice, "token-source-paying", "PAYING",
        payingAt, null, null, RETENTION_DAYS));
    AttemptEntity payingAttempt = attempt(payment(payingNotice), "token-source-paying");
    assertEquals(payingAt, payingAttempt.getAttemptStartedAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, payingAttempt.getStartTimestampBasis());
  }

  @Test
  @TestTransaction
  void ordinaryLogicalAndAttemptCorrectionsIncrementRevisionsOnceAndPublishSourceProvenance() throws Exception {
    String notice = "790000000000000007";
    Instant at = Instant.parse("2024-06-15T08:09:00Z");
    assertTrue(writer.reconcileLogical(PA, notice, "NOTIFIED", at));
    assertTrue(writer.reconcileAttempt(PA, notice, "token-correction", "NOTIFIED", at,
        null, null, RETENTION_DAYS));

    long paymentRevision = payment(notice).getProjectionRevision();
    long aggregateRevision = meta().getAggregateRevision();
    long sequence = meta().getLastSequence();
    long outboxCount = outboxCount(notice);

    Instant logicalSourceAt = at.plusSeconds(60);
    assertTrue(writer.reconcileLogical(PA, notice, "PAYING", logicalSourceAt));
    PaymentEntity afterLogical = payment(notice);
    assertEquals(paymentRevision + 1, afterLogical.getProjectionRevision());
    assertEquals(aggregateRevision + 1, meta().getAggregateRevision());
    assertEquals(sequence + 1, meta().getLastSequence());
    assertEquals(outboxCount + 1, outboxCount(notice));
    JsonNode logicalDelta = objectMapper.readTree(outbox(notice).getPaymentChanges());
    assertEquals("PAYING", logicalDelta.at("/logicalTransitions/0/state").asText());
    assertEquals("SOURCE_SYNC", logicalDelta.at("/logicalTransitions/0/timestampBasis").asText());

    paymentRevision = afterLogical.getProjectionRevision();
    aggregateRevision = meta().getAggregateRevision();
    sequence = meta().getLastSequence();
    outboxCount = outboxCount(notice);
    Instant attemptSourceAt = at.plusSeconds(120);
    assertTrue(writer.reconcileAttempt(PA, notice, "token-correction", "PAYING", attemptSourceAt,
        null, null, RETENTION_DAYS));

    assertEquals(paymentRevision + 1, payment(notice).getProjectionRevision());
    assertEquals(aggregateRevision + 1, meta().getAggregateRevision());
    assertEquals(sequence + 1, meta().getLastSequence());
    assertEquals(outboxCount + 1, outboxCount(notice));
    JsonNode attemptDelta = objectMapper.readTree(outbox(notice).getPaymentChanges());
    assertEquals("PAYING", attemptDelta.at("/attemptChanges/0/transitions/0/state").asText());
    assertEquals("SOURCE_SYNC", attemptDelta.at("/attemptChanges/0/transitions/0/timestampBasis").asText());
  }

  @Test
  @TestTransaction
  void sameStateRepeatsAreNoopsWithNoTimelineRevisionSequenceOrOutboxChanges() {
    String notice = "790000000000000008";
    Instant at = Instant.parse("2024-06-15T08:10:00Z");
    assertTrue(writer.reconcileLogical(PA, notice, "PAYING", at));
    assertTrue(writer.reconcileAttempt(PA, notice, "token-repeat", "PAYING", at,
        null, null, RETENTION_DAYS));

    PaymentEntity payment = payment(notice);
    int logicalSize = payment.getTimeline().size();
    int attemptSize = attempt(payment, "token-repeat").getTimeline().size();
    long paymentRevision = payment.getProjectionRevision();
    long aggregateRevision = meta().getAggregateRevision();
    long sequence = meta().getLastSequence();
    long outboxCount = outboxCount(notice);

    assertFalse(writer.reconcileLogical(PA, notice, "PAYING", at.plusSeconds(1)));
    assertFalse(writer.reconcileAttempt(PA, notice, "token-repeat", "PAYING", at.plusSeconds(1),
        null, null, RETENTION_DAYS));

    payment = payment(notice);
    assertEquals(logicalSize, payment.getTimeline().size());
    assertEquals(attemptSize, attempt(payment, "token-repeat").getTimeline().size());
    assertEquals(paymentRevision, payment.getProjectionRevision());
    assertEquals(aggregateRevision, meta().getAggregateRevision());
    assertEquals(sequence, meta().getLastSequence());
    assertEquals(outboxCount, outboxCount(notice));
  }

  @Test
  @TestTransaction
  void unsupportedSourceStatesDoNotCreateEntitiesOrMutateExistingProjection() {
    String missingNotice = "790000000000000009";
    MetaSnapshot before = metaSnapshot();
    long outboxBefore = outboxCount(missingNotice);

    assertFalse(writer.reconcileLogical(PA, missingNotice, null, Instant.parse("2024-06-15T08:11:00Z")));
    assertFalse(writer.reconcileAttempt(PA, missingNotice, null, "PAYMENT_RESERVED",
        Instant.parse("2024-06-15T08:11:00Z"), null, null, RETENTION_DAYS));
    assertFalse(writer.reconcileAttempt(PA, missingNotice, "token-unsupported", "PAYMENT_RESERVED",
        Instant.parse("2024-06-15T08:11:00Z"), "NOT_A_LOGICAL_STATE",
        Instant.parse("2024-06-15T08:11:00Z"), RETENTION_DAYS));

    assertNull(findPayment(missingNotice));
    assertEquals(0, transitionCount(missingNotice));
    assertEquals(before, metaSnapshot());
    assertEquals(outboxBefore, outboxCount(missingNotice));

    String existingNotice = "790000000000000010";
    Instant at = Instant.parse("2024-06-15T08:12:00Z");
    assertTrue(writer.reconcileLogical(PA, existingNotice, "PAYING", at));
    assertTrue(writer.reconcileAttempt(PA, existingNotice, "token-valid", "PAYMENT_RESERVED", at,
        null, null, RETENTION_DAYS));
    PaymentEntity payment = payment(existingNotice);
    MetaSnapshot existingMeta = metaSnapshot();
    int logicalSize = payment.getTimeline().size();
    int attemptSize = attempt(payment, "token-valid").getTimeline().size();
    String attemptState = attempt(payment, "token-valid").getCurrentState();
    long revision = payment.getProjectionRevision();
    long rows = outboxCount(existingNotice);

    assertFalse(writer.reconcileLogical(PA, existingNotice, "NOT_A_LOGICAL_STATE", at.plusSeconds(1)));
    assertFalse(writer.reconcileAttempt(PA, existingNotice, "token-valid", "NOT_AN_ATTEMPT_STATE",
        at.plusSeconds(1), null, null, RETENTION_DAYS));

    payment = payment(existingNotice);
    assertEquals("PAYING", payment.getCurrentState());
    assertEquals(logicalSize, payment.getTimeline().size());
    assertEquals(attemptSize, attempt(payment, "token-valid").getTimeline().size());
    assertEquals(attemptState, attempt(payment, "token-valid").getCurrentState());
    assertEquals(revision, payment.getProjectionRevision());
    assertEquals(existingMeta, metaSnapshot());
    assertEquals(rows, outboxCount(existingNotice));
  }

  @Test
  void concurrentFirstWritesForSamePaymentCreateOneProjection() throws Exception {
    String seedNotice = "790000000000000013";
    String notice = "790000000000000014";
    Instant at = Instant.parse("2024-06-15T08:14:00Z");
    assertTrue(writer.reconcileLogical(PA, seedNotice, "PAYING", at));

    int workers = 8;
    CyclicBarrier start = new CyclicBarrier(workers);
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    List<Future<Boolean>> results = new ArrayList<>();
    try {
      for (int i = 0; i < workers; i++) {
        results.add(executor.submit(() -> {
          start.await(5, TimeUnit.SECONDS);
          return writer.reconcileLogical(PA, notice, "PAYING", at);
        }));
      }
      long changed = 0;
      for (Future<Boolean> result : results) {
        if (result.get(10, TimeUnit.SECONDS)) {
          changed++;
        }
      }
      em.clear();
      assertEquals(1, changed);
      assertEquals(1, outboxCount(notice));
      assertEquals(1, payment(notice).getTimeline().size());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void concurrentWritesToDistinctPaymentsUseUniqueSequencesAndPreserveRevisions() throws Exception {
    String seedNotice = "790000000000000015";
    Instant at = Instant.parse("2024-06-15T08:15:00Z");
    assertTrue(writer.reconcileLogical(PA, seedNotice, "PAYING", at));
    em.clear();
    long aggregateRevision = meta().getAggregateRevision();
    long sequence = meta().getLastSequence();

    int workers = 8;
    CyclicBarrier start = new CyclicBarrier(workers);
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    List<Future<Boolean>> results = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    try {
      for (int i = 0; i < workers; i++) {
        String notice = "7900000000000001" + String.format("%02d", 20 + i);
        notices.add(notice);
        results.add(executor.submit(() -> {
          start.await(5, TimeUnit.SECONDS);
          return writer.reconcileLogical(PA, notice, "PAYING", at);
        }));
      }
      for (Future<Boolean> result : results) {
        assertTrue(result.get(10, TimeUnit.SECONDS));
      }

      em.clear();
      assertEquals(aggregateRevision + workers, meta().getAggregateRevision());
      assertEquals(sequence + workers, meta().getLastSequence());
      assertEquals(workers, em.createQuery(
              "SELECT COUNT(DISTINCT o.sequenceNo) FROM OutboxEntity o WHERE o.paFiscalCode = :pa "
                  + "AND o.noticeNumber IN :notices", Long.class)
          .setParameter("pa", PA).setParameter("notices", notices).getSingleResult());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @TestTransaction
  void missingOnlinePaymentIsNoopAndKnownPaymentStateIsReconciled() throws Exception {
    String missingNotice = "790000000000000030";
    assertFalse(syncService.syncPayment(PA, missingNotice).changed());
    assertNull(findPayment(missingNotice));

    String notice = "790000000000000031";
    LocalDateTime insertedAt = LocalDateTime.parse("2024-06-15T10:00:00");
    LocalDateTime updatedAt = LocalDateTime.parse("2024-06-15T10:05:00");
    insertLogicalSnapshot(1, notice, "PAID", insertedAt, updatedAt);

    OnlineStatusSyncService.SyncResult result = syncService.syncPayment(PA, notice);

    assertTrue(result.changed());
    assertEquals("PAID", result.state());
    PaymentEntity payment = payment(notice);
    assertEquals("PAID", payment.getCurrentState());
    assertEquals(1, payment.getTimeline().size());
    assertEquals(Instant.parse("2024-06-15T08:05:00Z"),
        payment.getTimeline().get(0).getTransitionAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, payment.getTimeline().get(0).getTimestampBasis());
  }

  @Test
  @TestTransaction
  void unsupportedOnlinePaymentStateFailsBeforeProjectionMutation() throws Exception {
    String notice = "790000000000000032";
    insertLogicalSnapshot(1, notice, "UNKNOWN_STATE", LocalDateTime.parse("2024-06-15T10:00:00"),
        LocalDateTime.parse("2024-06-15T10:01:00"));
    MetaSnapshot before = metaSnapshot();

    org.junit.jupiter.api.Assertions.assertThrows(OnlineSourceStateException.class,
        () -> syncService.syncPayment(PA, notice));

    assertNull(findPayment(notice));
    assertEquals(before, metaSnapshot());
    assertEquals(0, outboxCount(notice));
  }

  @Test
  @TestTransaction
  void attemptSyncUsesOwnTimeAndLogicalSnapshotTimeWhenCreatingParent() throws Exception {
    String notice = "790000000000000033";
    String token = "sync-token-parent";
    LocalDateTime logicalUpdated = LocalDateTime.parse("2024-06-15T10:10:00");
    LocalDateTime attemptUpdated = LocalDateTime.parse("2024-06-15T10:20:00");
    insertLogicalSnapshot(1, notice, "PAID", LocalDateTime.parse("2024-06-15T10:00:00"), logicalUpdated);
    insertAttemptSnapshot(1, notice, token, "PAYMENT_RESERVED",
        LocalDateTime.parse("2024-06-15T10:05:00"), attemptUpdated);

    OnlineStatusSyncService.SyncResult result = syncService.syncAttempt(PA, notice, token);

    assertTrue(result.changed());
    assertEquals("PAYMENT_RESERVED", result.state());
    PaymentEntity payment = payment(notice);
    AttemptEntity attempt = attempt(payment, token);
    assertEquals("PAID", payment.getCurrentState());
    assertEquals(Instant.parse("2024-06-15T08:10:00Z"), payment.getTimeline().get(0).getTransitionAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, payment.getTimeline().get(0).getTimestampBasis());
    assertEquals(Instant.parse("2024-06-15T08:20:00Z"), attempt.getTimeline().get(0).getTransitionAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, attempt.getTimeline().get(0).getTimestampBasis());
    assertEquals(1, outboxCount(notice));
  }

  @Test
  @TestTransaction
  void attemptSyncFallsBackToLogicalInsertedTimestampWhenLogicalUpdateIsNull() throws Exception {
    String notice = "790000000000000037";
    String token = "sync-token-logical-fallback";
    LocalDateTime logicalInserted = LocalDateTime.parse("2024-06-15T10:40:00");
    LocalDateTime attemptUpdated = LocalDateTime.parse("2024-06-15T10:45:00");
    insertLogicalSnapshot(1, notice, "PAID", logicalInserted, null);
    insertAttemptSnapshot(1, notice, token, "PAYMENT_RESERVED",
        LocalDateTime.parse("2024-06-15T10:42:00"), attemptUpdated);

    assertTrue(syncService.syncAttempt(PA, notice, token).changed());

    PaymentEntity payment = payment(notice);
    AttemptEntity attempt = attempt(payment, token);
    assertEquals(Instant.parse("2024-06-15T08:40:00Z"), payment.getTimeline().get(0).getTransitionAt());
    assertEquals(Instant.parse("2024-06-15T08:45:00Z"), attempt.getTimeline().get(0).getTransitionAt());
  }

  @Test
  @TestTransaction
  void attemptSyncPreservesInsertedTimestampWithoutInventingMissingOnlineParentState()
      throws Exception {
    String notice = "790000000000000034";
    String token = "sync-token-inferred";
    LocalDateTime insertedAt = LocalDateTime.parse("2024-06-15T10:30:00");
    insertAttemptSnapshot(1, notice, token, "PAYMENT_RESERVED", insertedAt, null);

    assertTrue(syncService.syncAttempt(PA, notice, token).changed());

    PaymentEntity payment = payment(notice);
    AttemptEntity attempt = attempt(payment, token);
    Instant expectedAt = Instant.parse("2024-06-15T08:30:00Z");
    assertNull(payment.getCurrentState());
    assertTrue(payment.getTimeline().isEmpty());
    assertTrue(payment.isTimelineIncomplete());
    assertEquals(expectedAt, attempt.getTimeline().get(0).getTransitionAt());
    assertEquals(TimestampBasis.SOURCE_SYNC, attempt.getTimeline().get(0).getTimestampBasis());
  }

  @Test
  @TestTransaction
  void existingProjectionParentSkipsLogicalOnlineLookup() throws Exception {
    String notice = "790000000000000035";
    String token = "sync-token-existing-parent";
    Instant at = Instant.parse("2024-06-15T08:35:00Z");
    assertTrue(writer.reconcileLogical(PA, notice, "PAYING", at));
    insertAttemptSnapshot(1, notice, token, "PAYING", LocalDateTime.parse("2024-06-15T10:35:00"),
        LocalDateTime.parse("2024-06-15T10:35:00"));
    try (Connection connection = onlineDataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE online.position_status_snapshot");
    }

    assertTrue(syncService.syncAttempt(PA, notice, token).changed());
    assertEquals("PAYING", payment(notice).getCurrentState());
  }

  @Test
  @TestTransaction
  void unsupportedAttemptOrLogicalSnapshotFailsBeforeCreatingProjection() throws Exception {
    String notice = "790000000000000036";
    String token = "sync-token-unsupported";
    LocalDateTime at = LocalDateTime.parse("2024-06-15T10:36:00");
    insertLogicalSnapshot(1, notice, "INVALID_LOGICAL_STATE", at, at);
    insertAttemptSnapshot(1, notice, token, "PAYMENT_RESERVED", at, at);
    MetaSnapshot before = metaSnapshot();

    org.junit.jupiter.api.Assertions.assertThrows(OnlineSourceStateException.class,
        () -> syncService.syncAttempt(PA, notice, token));

    assertNull(findPayment(notice));
    assertEquals(before, metaSnapshot());
    assertEquals(0, outboxCount(notice));
  }

  private PaymentEntity payment(String notice) {
    PaymentEntity result = findPayment(notice);
    assertNotNull(result, "payment should exist: " + notice);
    return result;
  }

  private PaymentEntity findPayment(String notice) {
    List<PaymentEntity> rows = em.createQuery(
            "SELECT DISTINCT p FROM PaymentEntity p LEFT JOIN FETCH p.timeline LEFT JOIN FETCH p.attempts "
                + "WHERE p.paFiscalCode = :pa AND p.noticeNumber = :notice",
            PaymentEntity.class)
        .setParameter("pa", PA).setParameter("notice", notice).getResultList();
    return rows.isEmpty() ? null : rows.get(0);
  }

  private AttemptEntity attempt(PaymentEntity payment, String token) {
    return payment.getAttempts().stream()
        .filter(candidate -> token.equals(candidate.getPaymentToken()))
        .findFirst().orElseThrow();
  }

  private ProjectionMetaEntity meta() {
    ProjectionMetaEntity result = em.find(ProjectionMetaEntity.class, 1);
    assertNotNull(result);
    return result;
  }

  private long metaRevisionOrDefault() {
    ProjectionMetaEntity result = em.find(ProjectionMetaEntity.class, 1);
    return result == null ? 1 : result.getAggregateRevision();
  }

  private long metaSequenceOrDefault() {
    ProjectionMetaEntity result = em.find(ProjectionMetaEntity.class, 1);
    return result == null ? 0 : result.getLastSequence();
  }

  private long outboxCount(String notice) {
    return em.createQuery("SELECT COUNT(o) FROM OutboxEntity o WHERE o.paFiscalCode = :pa "
            + "AND o.noticeNumber = :notice", Long.class)
        .setParameter("pa", PA).setParameter("notice", notice).getSingleResult();
  }

  private OutboxEntity outbox(String notice) {
    return em.createQuery("SELECT o FROM OutboxEntity o WHERE o.paFiscalCode = :pa "
            + "AND o.noticeNumber = :notice ORDER BY o.sequenceNo DESC", OutboxEntity.class)
        .setParameter("pa", PA).setParameter("notice", notice).setMaxResults(1).getSingleResult();
  }

  private long transitionCount(String notice) {
    return em.createQuery("SELECT COUNT(t) FROM LogicalTransitionEntity t WHERE t.payment.paFiscalCode = :pa "
            + "AND t.payment.noticeNumber = :notice", Long.class)
        .setParameter("pa", PA).setParameter("notice", notice).getSingleResult()
        + em.createQuery("SELECT COUNT(t) FROM AttemptTransitionEntity t "
            + "WHERE t.attempt.payment.paFiscalCode = :pa AND t.attempt.payment.noticeNumber = :notice",
            Long.class)
        .setParameter("pa", PA).setParameter("notice", notice).getSingleResult();
  }

  private MetaSnapshot metaSnapshot() {
    ProjectionMetaEntity meta = em.find(ProjectionMetaEntity.class, 1);
    return meta == null ? null : new MetaSnapshot(meta.getAggregateRevision(), meta.getLastSequence(),
        meta.getLastProcessedAt());
  }

  private record MetaSnapshot(long aggregateRevision, long lastSequence, Instant lastProcessedAt) {
  }

  private void insertLogicalSnapshot(long id, String notice, String state, LocalDateTime insertedAt,
      LocalDateTime updatedAt) throws SQLException {
    try (Connection connection = onlineDataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO online.position_status_snapshot
              (id, pa_fiscal_code, notice_id, status, inserted_timestamp, updated_timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """)) {
      statement.setLong(1, id);
      statement.setString(2, PA);
      statement.setString(3, notice);
      statement.setString(4, state);
      statement.setTimestamp(5, Timestamp.valueOf(insertedAt));
      statement.setTimestamp(6, updatedAt == null ? null : Timestamp.valueOf(updatedAt));
      statement.executeUpdate();
    }
  }

  private void insertAttemptSnapshot(long id, String notice, String token, String state,
      LocalDateTime insertedAt, LocalDateTime updatedAt) throws SQLException {
    try (Connection connection = onlineDataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO online.position_payment_status_snapshot
              (id, pa_fiscal_code, notice_id, payment_token, status, inserted_timestamp, updated_timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)) {
      statement.setLong(1, id);
      statement.setString(2, PA);
      statement.setString(3, notice);
      statement.setString(4, token);
      statement.setString(5, state);
      statement.setTimestamp(6, Timestamp.valueOf(insertedAt));
      statement.setTimestamp(7, updatedAt == null ? null : Timestamp.valueOf(updatedAt));
      statement.executeUpdate();
    }
  }
}
