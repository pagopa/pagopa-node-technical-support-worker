package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code counts}: payment/attempt totals of an aggregate snapshot. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CountsDto {
  private long payments;
  private long attempts;
}
