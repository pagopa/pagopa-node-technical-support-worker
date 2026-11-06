package it.gov.pagopa.nodetsworker.observability.repository.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single-row projection metadata: the global aggregate revision (bumped on every accepted change),
 * the latest outbox sequence, freshness state and the technical processing/publishing timestamps.
 */
@Entity
@Table(name = "projection_meta")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionMetaEntity {

  /** Always {@code 1}: there is exactly one metadata row. */
  @Id
  @Column(name = "id")
  private Integer id;

  @Column(name = "aggregate_revision", nullable = false)
  private long aggregateRevision;

  @Column(name = "last_sequence", nullable = false)
  private long lastSequence;

  @Column(name = "freshness_status", nullable = false, length = 16)
  private String freshnessStatus;

  @Column(name = "freshness_lag_seconds", nullable = false)
  private long freshnessLagSeconds;

  @Column(name = "freshness_gaps_detected", nullable = false)
  private boolean freshnessGapsDetected;

  @Column(name = "last_processed_at")
  private Instant lastProcessedAt;

  @Column(name = "last_published_at")
  private Instant lastPublishedAt;
}
