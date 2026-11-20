package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code RevealResponse}: the one place the raw payment token is returned in clear. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevealResponseDto {
  private String attemptId;
  private String paymentToken;
}
