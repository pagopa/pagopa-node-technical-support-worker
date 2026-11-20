package it.gov.pagopa.nodetsworker.observability.dto;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** A timeline entry as returned by the API (shared shape for logical and attempt timelines). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(requiredProperties = {"state", "transitionAt", "timestampBasis", "trigger"},
    additionalProperties = Schema.False.class,
    description = "Server transition order is authoritative, including repeated states and null or nonmonotonic dates.")
public class TimelineItemDto {
  private String state;
  @Schema(nullable = true, format = "date-time")
  private String transitionAt;
  private TimestampBasis timestampBasis;
  @Schema(nullable = true, description = "Original application trigger from the admitted RE event, when available.")
  private String trigger;
}
