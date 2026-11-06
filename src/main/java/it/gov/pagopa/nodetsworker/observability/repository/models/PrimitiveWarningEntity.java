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
 * One primitive call counted as a warning (spec §3.3.2): the request was not answered within the
 * response budget. It carries only the attributes of the request, never a payload, a payment token, a
 * notice number or the correlation {@code sessionId}. Dimensions use the empty string for "not available".
 */
@Entity
@Table(name = "primitive_warning",
    indexes = @Index(name = "idx_primitive_warning_called", columnList = "primitive, called_at"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrimitiveWarningEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "called_at", nullable = false)
  private Instant calledAt;

  @Column(name = "service_identifier", nullable = false, length = 64)
  private String serviceIdentifier;

  /** Original primitive name ({@code tipoEvento}), never normalized. */
  @Column(name = "primitive", nullable = false, length = 128)
  private String primitive;

  @Column(name = "psp", nullable = false, length = 64)
  private String psp;

  @Column(name = "canale", nullable = false, length = 64)
  private String canale;

  @Column(name = "pa_fiscal_code", nullable = false, length = 64)
  private String paFiscalCode;

  @Column(name = "stazione", nullable = false, length = 64)
  private String stazione;
}
