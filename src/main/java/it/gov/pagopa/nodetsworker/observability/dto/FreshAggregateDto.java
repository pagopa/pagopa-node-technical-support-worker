package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** An aggregate snapshot plus pipeline freshness: the common base of the overview and creditor detail. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class FreshAggregateDto extends AggregateDto {
  private FreshnessDto freshness;
}
