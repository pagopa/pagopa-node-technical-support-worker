package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code TokenLookupResponse}: the attempt a payment token resolves to (no token echoed). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenLookupResponseDto {
  private String paFiscalCode;
  private String noticeNumber;
  private String attemptId;
  private long projectionRevision;
}
