package it.gov.pagopa.nodetsworker.observability.repository.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Projection row for a receipt recipient of a logical payment. A recipient is identified by the
 * logical pair and the {@code stazione_recipient} discriminator (null = a single unidentified
 * recipient); the payment token is carried in the receipt but does not identify the recipient.
 */
@Entity
@Table(name = "recipient",
    uniqueConstraints = @UniqueConstraint(name = "uk_recipient_id", columnNames = "recipient_id"),
    indexes = {
        @Index(name = "idx_recipient_station", columnList = "stazione_recipient"),
        @Index(name = "idx_recipient_state", columnList = "current_state"),
        @Index(name = "idx_recipient_expires", columnList = "expires_at")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payment_id", nullable = false)
  private PaymentEntity payment;

  /** Stable technical id exposed by the API (never derived from the payment token). */
  @Column(name = "recipient_id", nullable = false, length = 32)
  private String recipientId;

  /** The {@code stazione_recipient} discriminator, or {@code null} when not supplied. */
  @Column(name = "stazione_recipient", length = 64)
  private String stazioneRecipient;

  @Column(name = "first_seen_at", nullable = false)
  private Instant firstSeenAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "timeline_incomplete", nullable = false)
  private boolean timelineIncomplete;

  @Column(name = "current_state", length = 32)
  private String currentState;

  @OneToMany(mappedBy = "recipient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @OrderColumn(name = "position")
  @Builder.Default
  private List<RecipientTransitionEntity> timeline = new ArrayList<>();
}
