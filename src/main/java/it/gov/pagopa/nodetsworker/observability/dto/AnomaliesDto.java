package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code anomalies}: count of ANOMALY transitions and of payments carrying at least one anomaly. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnomaliesDto {
  private long transitions;
  private long payments;
}
