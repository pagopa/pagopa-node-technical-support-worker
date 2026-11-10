package it.gov.pagopa.nodetsworker.observability.repository.models;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One accepted transition of an attempt timeline. */
@Entity
@Table(name = "attempt_transition")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptTransitionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attempt_id", nullable = false)
  private AttemptEntity attempt;

  @Column(name = "state", nullable = false, length = 32)
  private String state;

  @Column(name = "transition_at")
  private Instant transitionAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "timestamp_basis", nullable = false, length = 16)
  private TimestampBasis timestampBasis;

  @Column(name = "application_trigger", length = 512)
  private String trigger;
}
