package it.gov.pagopa.nodetsworker.observability.repository.models;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Projection row for a single payment attempt. Token/retention columns are technical metadata. */
@Entity
@Table(name = "attempt",
    uniqueConstraints = @UniqueConstraint(name = "uk_attempt_id", columnNames = "attempt_id"),
    indexes = {
        @Index(name = "idx_attempt_token", columnList = "payment_token"),
        @Index(name = "idx_attempt_psp", columnList = "psp"),
        @Index(name = "idx_attempt_canale", columnList = "canale"),
        @Index(name = "idx_attempt_stazione", columnList = "stazione"),
        @Index(name = "idx_attempt_state", columnList = "current_state"),
        @Index(name = "idx_attempt_expires", columnList = "expires_at")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_id", nullable = false)
  private PaymentEntity payment;

  @Column(name = "attempt_id", nullable = false, length = 64)
  private String attemptId;

  @Column(name = "payment_token", length = 128)
  private String paymentToken;

  @Column(name = "service_identifier", length = 64)
  private String serviceIdentifier;

  @Column(name = "psp", length = 64)
  private String psp;

  @Column(name = "stazione", length = 64)
  private String stazione;

  @Column(name = "canale", length = 64)
  private String canale;

  @Column(name = "attempt_started_at")
  private Instant attemptStartedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "start_timestamp_basis", nullable = false, length = 16)
  private TimestampBasis startTimestampBasis;

  @Column(name = "first_seen_at", nullable = false)
  private Instant firstSeenAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "timeline_incomplete", nullable = false)
  private boolean timelineIncomplete;

  @Column(name = "current_state", nullable = false, length = 32)
  private String currentState;

  @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @OrderColumn(name = "position")
  @Builder.Default
  private List<AttemptTransitionEntity> timeline = new ArrayList<>();
}
