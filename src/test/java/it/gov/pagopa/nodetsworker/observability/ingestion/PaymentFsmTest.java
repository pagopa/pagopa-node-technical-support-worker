package it.gov.pagopa.nodetsworker.observability.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.gov.pagopa.nodetsworker.observability.ingestion.AdmissionFilter.Entity;
import it.gov.pagopa.nodetsworker.observability.ingestion.PaymentFsm.Resolution;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FSM resolution, regression prevention and predecessor inference (spec §2, §4.3). */
class PaymentFsmTest {

  @Test
  void firstPayingAccepted() {
    Resolution r = PaymentFsm.resolve(Entity.PAYMENT, null, "PAYING");
    assertTrue(r.accepted());
    assertEquals(List.of("PAYING"), r.path());
    assertEquals(0, r.inferredCount());
  }

  @Test
  void infersUniquePredecessorsForLateFirstEvent() {
    // PAYMENT_RESERVED is inferable from PAYMENT_SENT, but the V1 PA OLD path can start
    // directly at PAYMENT_RESERVED, so PAYING is not inferred without evidence.
    Resolution r = PaymentFsm.resolve(Entity.PAYMENT, null, "PAYMENT_SENT");
    assertTrue(r.accepted());
    assertEquals(List.of("PAYMENT_RESERVED", "PAYMENT_SENT"), r.path());
    assertEquals(1, r.inferredCount());
    assertTrue(r.ambiguous());
  }

  @Test
  void firstReservedAttemptDoesNotInventPaying() {
    Resolution r = PaymentFsm.resolve(Entity.PAYMENT, null, "PAYMENT_RESERVED");
    assertTrue(r.accepted());
    assertEquals(List.of("PAYMENT_RESERVED"), r.path());
    assertEquals(0, r.inferredCount());
    assertTrue(r.ambiguous());
  }

  @Test
  void firstPaidAttemptDoesNotInventAcceptedFromStateAlone() {
    Resolution r = PaymentFsm.resolve(Entity.PAYMENT, null, "PAID");
    assertTrue(r.accepted());
    assertEquals(List.of("PAID"), r.path());
    assertTrue(r.ambiguous());
  }

  @Test
  void stateCatalogsContainOnlyDocumentedEnums() {
    assertEquals(5L, List.of("INSERTED", "PAYING", "PAID", "NOTICE_STORED", "NOTIFIED").stream()
        .filter(state -> PaymentFsm.isKnownState(Entity.POSITION, state)).count());
    assertEquals(18L, List.of("PAYING", "PAYING_RPT", "PAID_NORPT", "PAID", "FAILED_NORPT", "FAILED",
        "CANCELLED", "CANCELLED_NORPT", "NOTICE_GENERATED", "NOTICE_STORED", "NOTICE_SENT", "NOTIFIED",
        "PAYMENT_RESERVED", "PAYMENT_SENT", "PAYMENT_ACCEPTED", "PAYMENT_REFUSED", "PAYMENT_UNKNOWN",
        "PAYMENT_SEND_ERROR").stream().filter(state -> PaymentFsm.isKnownState(Entity.PAYMENT, state)).count());
    for (String unsupported : List.of("CANCELLED")) {
      assertFalse(PaymentFsm.isKnownState(Entity.POSITION, unsupported));
    }
    for (String unsupported : List.of("SENT", "ACCEPTED", "REFUSED", "SEND_ERROR", "UNKNOWN")) {
      assertFalse(PaymentFsm.isKnownState(Entity.PAYMENT, unsupported));
    }
  }

  @Test
  void directTransitionAccepted() {
    Resolution r = PaymentFsm.resolve(Entity.PAYMENT, "PAYMENT_SENT", "PAYMENT_UNKNOWN");
    assertTrue(r.accepted());
    assertEquals(List.of("PAYMENT_UNKNOWN"), r.path());
  }

  @Test
  void backwardTransitionRejected() {
    // Late activation after closePayment must not regress the attempt (criterion §8).
    assertFalse(PaymentFsm.resolve(Entity.PAYMENT, "PAYMENT_RESERVED", "PAYING").accepted());
  }

  @Test
  void terminalNotReopened() {
    assertFalse(PaymentFsm.resolve(Entity.PAYMENT, "CANCELLED", "PAYING").accepted());
    assertFalse(PaymentFsm.resolve(Entity.PAYMENT, "CANCELLED", "PAYMENT_SENT").accepted());
  }

  @Test
  void duplicateRejected() {
    assertFalse(PaymentFsm.resolve(Entity.PAYMENT, "PAYING", "PAYING").accepted());
  }

  @Test
  void ambiguousPathAppendsEventOnly() {
    // PAYING -> NOTIFIED has no direct edge and several compatible paths: do not invent history.
    Resolution r = PaymentFsm.resolve(Entity.PAYMENT, "PAYING", "NOTIFIED");
    assertTrue(r.accepted());
    assertTrue(r.ambiguous());
    assertEquals(List.of("NOTIFIED"), r.path());
  }

  @Test
  void logicalPaidAccepted() {
    assertTrue(PaymentFsm.resolve(Entity.POSITION, "PAYING", "PAID").accepted());
    assertFalse(PaymentFsm.resolve(Entity.POSITION, "PAID", "PAYING").accepted());
  }

  @Test
  void firstPositionStateDoesNotInventLegacyReceiptPath() {
    Resolution inserted = PaymentFsm.resolve(Entity.POSITION, null, "INSERTED");
    assertTrue(inserted.accepted());
    assertEquals(List.of("INSERTED"), inserted.path());

    Resolution receiptStored = PaymentFsm.resolve(Entity.POSITION, null, "NOTICE_STORED");
    assertTrue(receiptStored.accepted());
    assertEquals(List.of("NOTICE_STORED"), receiptStored.path());
    assertTrue(receiptStored.ambiguous());
  }

  @Test
  void recipientFirstNoticeGeneratedAccepted() {
    Resolution r = PaymentFsm.resolve(Entity.RECIPIENT, null, "NOTICE_GENERATED");
    assertTrue(r.accepted());
    assertEquals(List.of("NOTICE_GENERATED"), r.path());
    assertEquals(0, r.inferredCount());
    assertFalse(r.ambiguous());
  }

  @Test
  void recipientLateFirstEventInfersUniqueChain() {
    Resolution sent = PaymentFsm.resolve(Entity.RECIPIENT, null, "NOTICE_SENT");
    assertTrue(sent.accepted());
    assertEquals(List.of("NOTICE_GENERATED", "NOTICE_SENT"), sent.path());
    assertEquals(1, sent.inferredCount());
    assertFalse(sent.ambiguous());

    Resolution notified = PaymentFsm.resolve(Entity.RECIPIENT, null, "NOTIFIED");
    assertTrue(notified.accepted());
    assertEquals(List.of("NOTICE_GENERATED", "NOTICE_SENT", "NOTIFIED"), notified.path());
    assertEquals(2, notified.inferredCount());
  }

  @Test
  void recipientRetryLoopAndRegression() {
    assertTrue(PaymentFsm.resolve(Entity.RECIPIENT, "NOTICE_SENT", "NOTICE_PENDING").accepted());
    assertTrue(PaymentFsm.resolve(Entity.RECIPIENT, "NOTICE_PENDING", "NOTICE_SENT").accepted());
    assertFalse(PaymentFsm.resolve(Entity.RECIPIENT, "NOTICE_SENT", "NOTICE_SENT").accepted());
    // NOTIFIED is terminal for a recipient: a late send does not reopen it.
    assertFalse(PaymentFsm.resolve(Entity.RECIPIENT, "NOTIFIED", "NOTICE_SENT").accepted());
  }

  @Test
  void recipientStateCatalogHasFourDocumentedEnums() {
    assertEquals(4L, List.of("NOTICE_GENERATED", "NOTICE_SENT", "NOTICE_PENDING", "NOTIFIED").stream()
        .filter(state -> PaymentFsm.isKnownState(Entity.RECIPIENT, state)).count());
    assertFalse(PaymentFsm.isKnownState(Entity.RECIPIENT, "PAYING"));
  }

  @Test
  void attemptNotifiedIsNotDirectlyAppliedFromPaidWithoutReceiptEvidence() {
    Resolution r = PaymentFsm.resolve(Entity.PAYMENT, "PAID", "NOTIFIED");
    assertTrue(r.accepted());
    assertEquals(List.of("NOTIFIED"), r.path());
    assertTrue(r.ambiguous());

    Resolution position = PaymentFsm.resolve(Entity.POSITION, "PAID", "NOTIFIED");
    assertTrue(position.accepted());
    assertFalse(position.ambiguous());
  }
}
