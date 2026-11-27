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
 * Aggregated primitive counters for one minute and one dimension combination. Only counts are
 * persisted (spec §3.3.2); a call is attributed to the minute of its request. Dimensions use the
 * empty string for "not available".
 */
@Entity
@Table(name = "primitive_count",
    uniqueConstraints = @UniqueConstraint(name = "uk_primitive_count",
        columnNames = {"bucket_start", "service_identifier", "primitive", "direction", "psp", "canale", "stazione"}),
    indexes = @Index(name = "idx_primitive_count_bucket", columnList = "bucket_start"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrimitiveCountEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "bucket_start", nullable = false)
  private Instant bucketStart;

  @Column(name = "service_identifier", nullable = false, length = 64)
  private String serviceIdentifier;

  /** Original primitive name ({@code tipoEvento}), never normalized. */
  @Column(name = "primitive", nullable = false, length = 128)
  private String primitive;

  /** {@code PrimitiveDirection} name: the Nodo as erogatore (INBOUND), as fruitore (OUTBOUND), or UNKNOWN. */
  @Column(name = "direction", nullable = false, length = 16)
  private String direction;

  @Column(name = "psp", nullable = false, length = 64)
  private String psp;

  @Column(name = "canale", nullable = false, length = 64)
  private String canale;

  @Column(name = "stazione", nullable = false, length = 64)
  private String stazione;

  @Column(name = "requests", nullable = false)
  private long requests;

  @Column(name = "responses", nullable = false)
  private long responses;

  @Column(name = "warnings", nullable = false)
  private long warnings;
}
