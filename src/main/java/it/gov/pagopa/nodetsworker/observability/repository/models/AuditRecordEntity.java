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

/** Persistent audit of token exposure (reveal / lookup); never stores the token itself. */
@Entity
@Table(name = "audit_record", indexes = @Index(name = "idx_audit_recorded", columnList = "recorded_at"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecordEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "actor_id", nullable = false, length = 64)
  private String actorId;

  @Column(name = "action", nullable = false, length = 32)
  private String action;

  @Column(name = "attempt_id", length = 64)
  private String attemptId;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;
}
