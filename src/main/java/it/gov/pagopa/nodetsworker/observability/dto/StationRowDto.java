package it.gov.pagopa.nodetsworker.observability.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code StationRow}: one station of a creditor ({@code stazione} null = unavailable). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationRowDto {
  private String stazione;
  private List<TrafficBucketDto> traffic;
  private long startedAttempts;
  private List<StateCountDto> logicalStates;
  private List<StateCountDto> attemptStates;
  private CountsDto unresolved;
}
