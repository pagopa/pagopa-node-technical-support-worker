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
 * A technical ingestion anomaly (spec §4.1/§4.4), kept separate from the functional {@code ANOMALY}
 * timeline items already derived from a missing/unparseable event time. It records cases that cannot
 * be expressed on the projection itself: uncorrelatable messages (missing keys), a conflicting
 * non-null {@code psp}/{@code stazione}/{@code canale} value, a non-existent local time at the
 * spring-forward DST change, an unsupported state, or an FSM resolution that could not rebuild
 * predecessors unambiguously. Payment tokens are intentionally never stored here.
 */
@Entity
@Table(name = "ingestion_anomaly", indexes = {
    @Index(name = "idx_anomaly_recorded", columnList = "recorded_at"),
    @Index(name = "idx_anomaly_key", columnList = "pa_fiscal_code, notice_number")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionAnomalyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Technical diagnosis such as {@code MISSING_KEYS}, {@code UNKNOWN_STATE}, or an FSM anomaly. */
  @Column(name = "type", nullable = false, length = 32)
  private String type;

  @Column(name = "unique_id", length = 128)
  private String uniqueId;

  @Column(name = "pa_fiscal_code", length = 11)
  private String paFiscalCode;

  @Column(name = "notice_number", length = 18)
  private String noticeNumber;

  @Column(name = "payment_token", length = 128)
  private String paymentToken;

  @Column(name = "detail", length = 512)
  private String detail;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;
}
