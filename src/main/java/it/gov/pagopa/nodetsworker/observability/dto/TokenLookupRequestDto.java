package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contract schema {@code TokenLookupRequest}: the payment token to resolve to its attempt. The
 * optional {@code paFiscalCode}/{@code noticeNumber} are additional constraints (spec §4.1), not an
 * alternative to the token: when present they must match the resolved attempt's logical pair,
 * otherwise the lookup is a miss.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenLookupRequestDto {
  private String paymentToken;
  private String paFiscalCode;
  private String noticeNumber;
}
