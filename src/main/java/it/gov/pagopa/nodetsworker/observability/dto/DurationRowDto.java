package it.gov.pagopa.nodetsworker.observability.dto;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Contract schema {@code DurationRow}: duration statistics for a state transition over the window. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(requiredProperties = {"entity", "fromState", "toState", "timestampBasis", "samples",
    "averageMs", "medianMs", "p95Ms"}, additionalProperties = Schema.False.class)
public class DurationRowDto {
  @Schema(enumeration = {"logical", "attempt", "recipient"})
  private String entity;
  private String fromState;
  private String toState;
  private TimestampBasis timestampBasis;
  private long samples;
  private double averageMs;
  private long medianMs;
  private long p95Ms;
}
