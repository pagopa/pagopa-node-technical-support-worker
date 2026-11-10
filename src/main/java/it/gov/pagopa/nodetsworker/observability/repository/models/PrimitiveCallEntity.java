package it.gov.pagopa.nodetsworker.observability.repository.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transient work row pairing the REQ and RESP interface events of one primitive call, keyed by
 * ({@code sessionId}, {@code tipoEvento}). It exists only until the call is settled (both events
 * counted) or the pending TTL elapses; it never carries a payload or a payment key. Dimensions use
 * the empty string for "not available" so they can take part in unique keys.
 */
@Entity
@Table(name = "primitive_call",
    uniqueConstraints = @UniqueConstraint(name = "uk_primitive_call", columnNames = {"session_id", "tipo_evento"}),
    indexes = {
        @Index(name = "idx_primitive_call_pending", columnList = "warned, request_at"),
        @Index(name = "idx_primitive_call_created", columnList = "created_at")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrimitiveCallEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, length = 128)
  private String sessionId;

  @Column(name = "tipo_evento", nullable = false, length = 128)
  private String tipoEvento;

  @Column(name = "request_at")
  private Instant requestAt;

  @Column(name = "response_at")
  private Instant responseAt;

  @Column(name = "service_identifier", nullable = false, length = 64)
  private String serviceIdentifier;

  /** {@code PrimitiveDirection} name, from the RE parties of the request. */
  @Column(name = "direction", nullable = false, length = 16)
  private String direction;

  @Column(name = "psp", nullable = false, length = 64)
  private String psp;

  @Column(name = "canale", nullable = false, length = 64)
  private String canale;

  @Column(name = "stazione", nullable = false, length = 64)
  private String stazione;

  /** Creditor entity of the request ({@code idDominio}); kept only to describe a warning. */
  @Column(name = "pa_fiscal_code", nullable = false, length = 64)
  private String paFiscalCode;

  /** True once the call has been counted as a warning (late response or timeout), exactly once. */
  @Column(name = "warned", nullable = false)
  private boolean warned;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
