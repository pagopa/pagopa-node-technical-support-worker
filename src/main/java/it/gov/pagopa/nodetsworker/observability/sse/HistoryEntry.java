package it.gov.pagopa.nodetsworker.observability.sse;

import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveBucket;
import it.gov.pagopa.nodetsworker.observability.dto.FreshnessDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One published change kept in the bounded replay history. Aggregate deltas are recomputed per
 * subscriber from {@code before}/{@code after} (and the signed {@code primitiveChanges});
 * payment deltas are forwarded from {@code paymentChanges}.
 */
public record HistoryEntry(
    long sequence,
    long revision,
    Instant createdAt,
    List<DomainPayment> before,
    List<DomainPayment> after,
    List<PaymentChange> paymentChanges,
    FreshnessDto freshness,
    List<PrimitiveBucket> primitiveChanges) {

  /** An entry without primitive counter changes. */
  public HistoryEntry(long sequence, long revision, Instant createdAt, List<DomainPayment> before,
      List<DomainPayment> after, List<PaymentChange> paymentChanges, FreshnessDto freshness) {
    this(sequence, revision, createdAt, before, after, paymentChanges, freshness, List.of());
  }

  /** A ready-made payment-delta body for a single logical payment, produced by the projection writer. */
  public record PaymentChange(String paFiscalCode, String noticeNumber, long projectionRevision,
      Map<String, Object> changes, boolean suppressed) {
  }
}
