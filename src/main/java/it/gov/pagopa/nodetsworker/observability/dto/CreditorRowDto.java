package it.gov.pagopa.nodetsworker.observability.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code CreditorRow}: one creditor in the creditors list. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditorRowDto {
  private String paFiscalCode;
  private long startedAttempts;
  private CountsDto counts;
  private List<StateCountDto> logicalStates;
  private CountsDto unresolved;
}
