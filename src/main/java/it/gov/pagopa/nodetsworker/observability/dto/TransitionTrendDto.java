package it.gov.pagopa.nodetsworker.observability.dto;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Contract schema {@code TransitionTrend}: transitions in a UTC day bucket by entity/state/basis. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(requiredProperties = {"bucketStart", "entity", "state", "timestampBasis", "count"},
    additionalProperties = Schema.False.class)
public class TransitionTrendDto {
  private String bucketStart;
  @Schema(enumeration = {"logical", "attempt", "recipient"})
  private String entity;
  private String state;
  private TimestampBasis timestampBasis;
  private long count;
}
