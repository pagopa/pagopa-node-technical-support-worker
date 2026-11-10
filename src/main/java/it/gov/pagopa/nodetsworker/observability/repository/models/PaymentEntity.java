package it.gov.pagopa.nodetsworker.observability.repository.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Projection row for a logical payment identified by {@code (paFiscalCode, noticeNumber)}. */
@Entity
@Table(name = "payment",
    uniqueConstraints = @UniqueConstraint(name = "uk_payment_key", columnNames = {"pa_fiscal_code", "notice_number"}),
    indexes = {
        @Index(name = "idx_payment_key", columnList = "pa_fiscal_code, notice_number"),
        @Index(name = "idx_payment_pa", columnList = "pa_fiscal_code"),
        @Index(name = "idx_payment_state", columnList = "current_state")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "pa_fiscal_code", nullable = false, length = 11)
  private String paFiscalCode;

  @Column(name = "notice_number", nullable = false, length = 18)
  private String noticeNumber;

  @Column(name = "projection_revision", nullable = false)
  private long projectionRevision;

  @Column(name = "timeline_incomplete", nullable = false)
  private boolean timelineIncomplete;

  @Column(name = "current_state", length = 32)
  private String currentState;

  @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.LAZY)
  @OrderColumn(name = "position")
  @Builder.Default
  private List<LogicalTransitionEntity> timeline = new ArrayList<>();

  @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.LAZY)
  @OrderColumn(name = "position")
  @Builder.Default
  private List<AttemptEntity> attempts = new ArrayList<>();

  @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.LAZY)
  @OrderColumn(name = "position")
  @Builder.Default
  private List<RecipientEntity> recipients = new ArrayList<>();
}
