package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code TrafficBucket}: attempts started in a UTC day bucket. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrafficBucketDto {
  private String bucketStart;
  private long startedAttempts;
  private long estimatedStarts;
}
