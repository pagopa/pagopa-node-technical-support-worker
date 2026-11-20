package it.gov.pagopa.nodetsworker.observability.repository.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbox row written in the same transaction as a projection change (by the Event Hubs consumer, out
 * of scope here). The SSE publisher polls unpublished rows and fans out payment/aggregate deltas.
 * {@code paFiscalCode}/{@code noticeNumber} identify the affected logical payment; for an
 * aggregate-only change (e.g. freshness) they are null and {@code paymentChanges} is absent.
 */
@Entity
@Table(name = "outbox_event", indexes = {
    @Index(name = "idx_outbox_seq", columnList = "sequence_no"),
    @Index(name = "idx_outbox_published", columnList = "published, sequence_no")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Monotonic publication order; also the basis of resumable SSE cursors. */
  @Column(name = "sequence_no", nullable = false, unique = true)
  private long sequenceNo;

  @Column(name = "aggregate_revision", nullable = false)
  private long aggregateRevision;

  @Column(name = "pa_fiscal_code", length = 11)
  private String paFiscalCode;

  @Column(name = "notice_number", length = 18)
  private String noticeNumber;

  @Column(name = "payment_revision")
  private Long paymentRevision;

  /** Ready-made {@code changes} object of the payment delta (JSON), produced by the writer. */
  @Column(name = "payment_changes", columnDefinition = "text")
  private String paymentChanges;

  /** Signed primitive counter changes (JSON array of {@code PrimitiveBucket}), for aggregate-only rows. */
  @Column(name = "primitive_changes", columnDefinition = "text")
  private String primitiveChanges;

  /** When true the payment delta is suppressed for payment subscribers (revision-gap scenario). */
  @Column(name = "suppress_payment", nullable = false)
  private boolean suppressPayment;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published", nullable = false)
  private boolean published;

  @Column(name = "published_at")
  private Instant publishedAt;
}
