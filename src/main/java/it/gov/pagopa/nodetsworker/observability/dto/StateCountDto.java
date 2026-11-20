package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A {state, count} entry (schemas {@code LogicalStateCount} / {@code AttemptStateCount}). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StateCountDto {
  private String state;
  private long count;
}
