package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code Freshness}: pipeline lag/gap indicators and processing timestamps. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreshnessDto {
  /** One of CURRENT, DELAYED, GAP. */
  private String status;
  private long lagSeconds;
  private boolean gapsDetected;
  private String lastProcessedAt;
  private String lastPublishedAt;
  private String observedAt;
}
