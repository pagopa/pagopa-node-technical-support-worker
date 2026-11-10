package it.gov.pagopa.nodetsworker.observability.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code Window}: the half-open {@code [from, to)} UTC time window of an aggregate. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WindowDto {
  @JsonProperty("from")
  private String from;

  @JsonProperty("to")
  private String to;
}
