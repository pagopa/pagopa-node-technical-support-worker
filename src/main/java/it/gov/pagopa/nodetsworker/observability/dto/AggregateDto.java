package it.gov.pagopa.nodetsworker.observability.dto;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code Aggregate}: an aggregate snapshot over a filtered projection and window. */
@Data
@NoArgsConstructor
public class AggregateDto {
  private String snapshotAt;
  private long projectionRevision;
  private WindowDto window;
  private CountsDto counts;
  private List<StateCountDto> logicalStates;
  private List<StateCountDto> attemptStates;
  private List<StateCountDto> recipientStates;
  private CountsDto unresolved;
  private MatricesDto matrices;
  private List<TrafficBucketDto> traffic;
  private List<TransitionTrendDto> transitions;
  private List<DurationRowDto> durations;
  private AnomaliesDto anomalies;
}
