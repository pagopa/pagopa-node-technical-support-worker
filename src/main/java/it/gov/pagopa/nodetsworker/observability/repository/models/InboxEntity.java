package it.gov.pagopa.nodetsworker.observability.repository.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Technical inbox record for Event Hubs deduplication (spec §4.4). The primary key is the RE
 * {@code uniqueId}; when a message carries none, the ingestion stores a SHA-256-derived synthetic id
 * for the transport key {@code (eventHub, partitionId, sequenceNumber)}. A redelivery of the same
 * message hits the same row and is replayed as a no-op, so the FSM never reapplies the transition.
 */
@Entity
@Table(name = "ingestion_inbox", indexes = @Index(name = "idx_inbox_expires", columnList = "expires_at"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxEntity {

  /** RE {@code uniqueId}, or a namespaced digest of the transport-key fallback. */
  @Id
  @Column(name = "unique_id", nullable = false, length = 128)
  private String uniqueId;

  @Column(name = "event_hub", length = 512)
  private String eventHub;

  @Column(name = "partition_id", length = 32)
  private String partitionId;

  @Column(name = "sequence_number")
  private Long sequenceNumber;

  /** Processing outcome: {@code APPLIED}, {@code NOOP} or {@code ANOMALY}. */
  @Column(name = "outcome", nullable = false, length = 16)
  private String outcome;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
}
